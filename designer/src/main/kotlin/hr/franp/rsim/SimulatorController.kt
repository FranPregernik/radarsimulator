package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.application.Platform.*
import javafx.beans.property.*
import net.schmizz.sshj.*
import net.schmizz.sshj.common.*
import net.schmizz.sshj.xfer.*
import org.apache.commons.math3.stat.regression.*
import tornadofx.*
import java.io.*
import java.lang.Math.*
import java.util.concurrent.*

class SimulatorController : Controller(), AutoCloseable {
    var radarParameters by property(RadarParameters(
        impulsePeriodUs = 3003.0,
        impulseSignalUs = 3.0,
        maxImpulsePeriodUs = 3072.0,
        seekTimeSec = 12.0,
        azimuthChangePulse = 4096,
        horizontalAngleBeamWidthDeg = 1.4,
        distanceResolutionKm = 0.150,
        maxRadarDistanceKm = 400.0,
        minRadarDistanceKm = 5.0
    ))

    val radarParametersProperty = getProperty(SimulatorController::radarParameters)


    private val timeShiftFunc = SimpleRegression()
    private val acpIdxFunc = SimpleRegression()

    private val sshClient = SSHClient().apply {
        // no need to verify, not really security oriented
        addHostKeyVerifier { _, _, _ -> true }
        useCompression()
    }

    private fun connect() {
        sshClient.apply {
            if (isConnected)
                return

            try {
                connect(config.string("simulatorIp"))

                // again security here is not an issue - petalinux default login
                authPassword(
                    config.string("username", "root"),
                    config.string("password", "root")
                )
            } catch (ex: Exception) {
                throw RuntimeException("Unable to connect to simulator HW")
            }

        }

    }

    override fun close() {
        sshClient.close()
    }

    val simulationRunningProperty = SimpleBooleanProperty(false)

    fun uploadClutterFile(
        file: FileSystemFile,
        progressConsumer: (Double, String) -> Unit) {

        connect()
        stopSimulation()

        sshClient.apply {
            newSCPFileTransfer().apply {
                transferListener = object : TransferListener {
                    override fun directory(name: String?): TransferListener {
                        return this
                    }

                    override fun file(name: String?, size: Long): StreamCopier.Listener {
                        return StreamCopier.Listener { transferred ->
                            progressConsumer(transferred.toDouble() / size.toDouble(), "Transferring $name")
                        }
                    }
                }

                upload(file, "/var/clutter.bin.gz")
            }

            // cleanup
            startSession().use { it.exec("rm -rf /var/clutter.bin").join() }
            progressConsumer(0.0, "Unpacking clutter ...")
            startSession().use { it.exec("gunzip -f /var/clutter.bin.gz").join() }
            progressConsumer(1.0, "Done")

        }

    }

    fun uploadTargetsFile(
        file: FileSystemFile,
        progressConsumer: (Double, String) -> Unit) {

        connect()
        stopSimulation()

        sshClient.apply {
            newSCPFileTransfer().apply {
                transferListener = object : TransferListener {
                    override fun directory(name: String?): TransferListener {
                        return this
                    }

                    override fun file(name: String?, size: Long): StreamCopier.Listener {
                        return StreamCopier.Listener { transferred ->
                            progressConsumer(transferred.toDouble() / size.toDouble(), "Transferring $name")
                        }
                    }
                }
                upload(file, "/var/targets.bin.gz")
            }

            // cleanup
            startSession().use { it.exec("rm -rf /var/targets.bin").join() }
            progressConsumer(0.0, "Unpacking targets ...")
            startSession().use { it.exec("gunzip -f /var/targets.bin.gz").join() }
            progressConsumer(1.0, "Done")
        }
    }

    fun startSimulation(progressConsumer: (Double, String) -> Unit) {

        try {
            connect()
            stopSimulation()
            calibrate()

            timeShiftFunc.clear()
            acpIdxFunc.clear()

            sshClient.apply {

                // start sim
                val reg = "\\w+=(\\d+)(/(\\d+))?".toRegex()

                startSession().use { session ->

                    val cmd = session.exec("radar-sim-test -r --load-clutter-file /var/clutter.bin --load-target-file /var/targets.bin")

                    runLater {
                        simulationRunningProperty.set(true)
                    }

                    cmd.inputStream.use { stdout ->
                        progressConsumer(0.0, "Running simulation")
                        InputStreamReader(stdout).use { stdOutReader ->
                            stdOutReader.forEachLine { line ->
                                val t = System.currentTimeMillis().toDouble()
                                log.finest { line }
                                if (line.startsWith("SIM_ACP_IDX")) {
                                    val matchResult = reg.matchEntire(line) ?: return@forEachLine
                                    val values = matchResult.groupValues
                                    if (values.size < 3) {
                                        return@forEachLine
                                    }

                                    val acpIdx = values[1].toLong()
                                    val simTime = values[3].toLong()
                                    timeShiftFunc.addData(t, simTime.toDouble())
                                    if (acpIdx > 0) {
                                        acpIdxFunc.addData(simTime.toDouble(), acpIdx.toDouble())
                                    }
                                } else if (line.startsWith("DISABLE_SIM")) {
                                    runLater {
                                        simulationRunningProperty.set(false)
                                    }
                                }
                            }
                        }
                    }

                    cmd.errorStream.use { stdout ->
                        InputStreamReader(stdout).use { stdOutReader ->
                            stdOutReader.forEachLine { line ->
                                log.severe { line }
                            }
                        }
                    }

                    // wait for termination
                    cmd.join()

                    val exitStatus = cmd.exitStatus ?: 0
                    if (exitStatus > 0) {
                        log.info { "Command exited with $exitStatus and message: \"${cmd.exitErrorMessage}\"" }
                    }
                }
            }

        } finally {
            runLater {
                simulationRunningProperty.set(false)
            }
        }
    }

    fun approxSimAcp(t: Double = System.currentTimeMillis().toDouble()): Long {
        val simTime = timeShiftFunc.predict(t)
        val acpIdx = acpIdxFunc.predict(simTime)
        return acpIdx.toLong()
    }

    fun approxSimTime(t: Double = System.currentTimeMillis().toDouble()) =
        approxSimAcp(t).toDouble() / radarParameters.azimuthChangePulse * radarParameters.seekTimeSec

    fun approxAzimuth(t: Double = System.currentTimeMillis().toDouble()) = toDegrees(
        azimuthToAngle(TWO_PI * approxSimAcp(t) / radarParameters.azimuthChangePulse)
    )

    fun stopSimulation() {
        connect()

        try {
            sshClient.apply {
                // cleanup
                startSession().use { it.exec("killall radar-sim-test").join() }
            }
        } finally {
            runLater {
                simulationRunningProperty.set(false)
            }
        }
    }

    fun calibrate() {

        connect()
        stopSimulation()

        var arpUs = 0.0
        var acpCnt = 0
        var trigUs = 0.0
        var calibrated = false
        val valueRegex = "\\w+=(\\d+)".toRegex()

        sshClient.apply {

            // calibrate sim
            startSession().use { session ->

                val cmd = session.exec("radar-sim-test -c")
                cmd.inputStream.use command@ { stdout ->
                    InputStreamReader(stdout).use { stdOutReader ->
                        stdOutReader.forEachLine {
                            log.info { it }
                            val matchedValue = valueRegex.matchEntire(it)?.groups?.get(1)?.value
                            if (it.startsWith("SIM_ARP_US")) {
                                arpUs = matchedValue?.toDouble() ?: 0.0
                            } else if (it.startsWith("SIM_ACP_CNT")) {
                                acpCnt = 2 * (matchedValue?.toInt() ?: 0)
                            } else if (it.startsWith("SIM_TRIG_US")) {
                                trigUs = matchedValue?.toDouble() ?: 0.0
                            } else if (it.startsWith("SIM_CAL")) {
                                calibrated = (matchedValue?.toInt() ?: 0) > 0
                            }
                        }
                    }
                }

                cmd.errorStream.use { stdout ->
                    InputStreamReader(stdout).use { stdOutReader ->
                        stdOutReader.forEachLine { line ->
                            log.severe { line }
                        }
                    }
                }

                // wait for termination
                cmd.join(1, TimeUnit.MINUTES)

                val exitStatus = cmd.exitStatus ?: 0
                if (!calibrated && exitStatus > 0) {
                    log.info { "Command exited with $exitStatus and message: \"${cmd.exitErrorMessage}\"" }
                }
            }
        }

        if (!calibrated) {
            throw RuntimeException("Unable to calibrate")
        }

        radarParameters = radarParameters.copy(
            seekTimeSec = arpUs / S_TO_US,
            azimuthChangePulse = acpCnt,
            impulsePeriodUs = trigUs
        )

    }
}

