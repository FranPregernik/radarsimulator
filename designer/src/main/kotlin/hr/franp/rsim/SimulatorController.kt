package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.application.Platform.*
import javafx.beans.property.*
import net.schmizz.sshj.*
import net.schmizz.sshj.common.*
import net.schmizz.sshj.xfer.*
import tornadofx.*
import java.io.*
import java.util.concurrent.*

class SimulatorController : Controller() {

    var radarParameters by property(RadarParameters(
        impulsePeriodUs = 3003.0,
        maxImpulsePeriodUs = 3072.0,
        seekTimeSec = 12.0,
        azimuthChangePulse = 4096,
        horizontalAngleBeamWidthDeg = 1.4,
        distanceResolutionKm = 0.150,
        maxRadarDistanceKm = 400.0,
        minRadarDistanceKm = 5.0
    ))

    val radarParametersProperty = getProperty(SimulatorController::radarParameters)

    private val sshClient = SSHClient().apply {
        // no need to verify, not really security oriented
        addHostKeyVerifier { _, _, _ -> true }

        useCompression()

        connect("192.168.0.108")

        // again security here is not an issue - petalinux default login
        authPassword("root", "root")
    }

    val simulationRunningProperty = SimpleBooleanProperty(false)

    /**
     * Current time in the simulation
     */
    val currentTimeSecProperty = SimpleDoubleProperty(0.0)

    fun uploadClutterFile(
        file: FileSystemFile,
        progressConsumer: (Double, String) -> Unit) {

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

        stopSimulation()

        sshClient.apply {

            // start sim
            val reg = "\\w+=(\\d+)(/(\\d+))?".toRegex()

            startSession().use { session ->

                val cmd = session.exec("/mnt/radar-sim-test -r --load-clutter-file /var/clutter.bin --load-target-file /var/targets.bin")

                runLater {
                    simulationRunningProperty.set(true)
                }

                var azimuthChangePulse: Double = Double.NaN
                var seekTimeSec: Double = Double.NaN

                cmd.inputStream.use { stdout ->
                    progressConsumer(0.0, "Running simulation")
                    InputStreamReader(stdout).use { stdOutReader ->
                        stdOutReader.forEachLine { line ->
                            log.info { line }
                            if (line.startsWith("SIM_ACP_CNT")) {
                                azimuthChangePulse = reg.matchEntire(line)?.groups?.get(1)?.value?.toDouble() ?: 0.0
                                log.info { azimuthChangePulse.toString() }
                            } else if (line.startsWith("SIM_ARP_US")) {
                                seekTimeSec = (reg.matchEntire(line)?.groups?.get(1)?.value?.toDouble() ?: 0.0) / S_TO_US
                                log.info { seekTimeSec.toString() }
                            } else if (line.startsWith("SIM_ACP_IDX")) {
                                if (!(azimuthChangePulse.isNaN() || seekTimeSec.isNaN())) {
                                    runLater {
                                        currentTimeSecProperty.set(
                                            (reg.matchEntire(line)?.groups?.get(1)?.value?.toDouble() ?: 0.0) / azimuthChangePulse * seekTimeSec
                                        )
                                    }
                                }
                            } else if (line.startsWith("DISABLE_SIM")) {
                                runLater {
                                    simulationRunningProperty.set(false)
                                }
                            }
                        }
                    }
                }
                cmd.join()
            }
        }

    }

    fun stopSimulation() {
        try {
            sshClient.apply {
                // cleanup
                startSession().use { it.exec("killall radar-sim-test").join() }
                startSession().use { it.exec("killall -9 radar-sim-test").join() }
            }
        } finally {
            runLater {
                simulationRunningProperty.set(false)
            }
        }
    }

    fun calibrate() {

        stopSimulation()

        var arpUs = 0.0
        var acpCnt = 0
        var trigUs = 0.0
        var calibrated = false
        val valueRegex = "\\w+=(\\d+)".toRegex()

        sshClient.apply {

            // calibrate sim
            startSession().use { session ->
                val cmd = session.exec("/mnt/radar-sim-test -c")
                cmd.inputStream.use command@ { stdout ->
                    InputStreamReader(stdout).use { stdOutReader ->
                        stdOutReader.forEachLine {
                            log.info { it }
                            val matchedValue = valueRegex.matchEntire(it)?.groups?.get(1)?.value
                            if (it.startsWith("SIM_ARP_US")) {
                                arpUs = matchedValue?.toDouble() ?: 0.0
                            } else if (it.startsWith("SIM_ACP_CNT")) {
                                acpCnt = matchedValue?.toInt() ?: 0
                            } else if (it.startsWith("SIM_TRIG_US")) {
                                trigUs = matchedValue?.toDouble() ?: 0.0
                            } else if (it.startsWith("SIM_CAL")) {
                                calibrated = (matchedValue?.toInt() ?: 0) > 0
                            }
                        }
                    }
                }
                cmd.join(1, TimeUnit.MINUTES)
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