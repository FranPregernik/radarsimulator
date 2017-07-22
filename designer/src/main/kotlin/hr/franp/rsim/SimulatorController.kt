package hr.franp.rsim

import hr.franp.rsim.Simulator.Client
import hr.franp.rsim.helpers.ReconnectingThriftClient.wrap
import hr.franp.rsim.models.RadarParameters
import javafx.application.Platform.runLater
import javafx.beans.property.SimpleBooleanProperty
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.common.StreamCopier
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.Signal
import net.schmizz.sshj.xfer.FileSystemFile
import net.schmizz.sshj.xfer.TransferListener
import org.apache.commons.math3.stat.regression.SimpleRegression
import org.apache.thrift.TException
import org.apache.thrift.protocol.TBinaryProtocol
import org.apache.thrift.transport.TSocket
import org.apache.thrift.transport.TTransportException
import tornadofx.*
import java.lang.Math.floor
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import kotlin.concurrent.thread
import kotlin.concurrent.timer

class SimulatorController : Controller(), AutoCloseable {

    private val simulatorClient: Simulator.Iface

    private val serverCommand: Session.Command

    var radarParameters by property(RadarParameters(
        impulsePeriodUs = 3003.0,
        impulseSignalUs = 3.0,
        maxImpulsePeriodUs = 3072.0,
        seekTimeSec = 12.0,
        azimuthChangePulse = 4096,
        horizontalAngleBeamWidthDeg = 1.4,
        distanceResolutionKm = 0.150,
        maxRadarDistanceKm = 370.0,
        minRadarDistanceKm = 5.0
    ))

    val radarParametersProperty = getProperty(SimulatorController::radarParameters)

    private val timeShiftFunc = SimpleRegression()

    private val acpIdxFunc = SimpleRegression()
    private val sshClient: SSHClient

    val simulationRunningProperty = SimpleBooleanProperty(false)

    private val statusTimer: Timer

    private val stdOutLoggingThread: Thread
    private val stdErrLoggingThread: Thread

    init {
        sshClient = initSsh()

        val session = sshClient.startSession()
        serverCommand = session.exec("radar_sim_server")

        var boot = false

        stdOutLoggingThread = thread(name = "sim_server_stdout") {
            try {
                serverCommand.inputStream.bufferedReader().use { r ->
                    while (true) {
                        val line = r.readLine()
                        log.info(line)

                        if (line.contains("STARTED_SERVER")) {
                            boot = true
                        }
                    }
                }
            } catch (ignore: Exception) {
                // ignore
            }
        }

        stdErrLoggingThread = thread(name = "sim_server_stderr") {
            try {
                serverCommand.errorStream.bufferedReader().use { r ->
                    while (true) {
                        log.info(r.readLine())
                    }
                }
            } catch (ignore: Exception) {
                // ignore
            }
        }

        // wait for server boot
        while (!boot) {
            Thread.sleep(1000)
        }

        simulatorClient = initSimulator()

        try {
            simulatorClient.reset()
        } catch (x: TException) {
            throw RuntimeException("Unable to reset simulator HW", x)
        }

        statusTimer = timer("sim_status", period = 5000L) {
            try {
                synchronized(simulatorClient) {
                    simulatorClient.state
                }.apply {
                    log.log(Level.INFO, "CURR_ACP_IDX $currAcpIdx")
                    log.log(Level.INFO, "SIM_ACP_IDX $simAcpIdx")
                    log.log(Level.INFO, "CLUTTER_ACP_IDX $loadedClutterAcpIndex")
                    log.log(Level.INFO, "TARGET_ACP_IDX $loadedTargetAcpIndex")
                    log.log(Level.INFO, "CLUTTER_ACP $loadedClutterAcp")
                    log.log(Level.INFO, "TARGET_ACP $loadedTargetAcp")
                }
            } catch (e: Exception) {
                log.log(Level.WARNING, e.message)
            }
        }
    }

    override fun close() {
        statusTimer.cancel()

        stdOutLoggingThread.interrupt()
        stdOutLoggingThread.join()

        stdErrLoggingThread.interrupt()
        stdErrLoggingThread.join()

        serverCommand.signal(Signal.TERM)
        serverCommand.join()

        sshClient.close()
    }

    private fun initSsh() = SSHClient().apply {
        // no need to verify, not really security oriented
        addHostKeyVerifier { _, _, _ -> true }
        useCompression()

        timeout = 2000

        try {
            connect(config.string("simulatorIp"))

            // again security here is not an issue - petalinux default login
            authPassword(
                config.string("username", "root"),
                config.string("password", "root")
            )
        } catch (ex: Exception) {
            throw RuntimeException("Unable to connect to simulator HW", ex)
        }

        // terminate old stuck servers
        startSession().use { session ->
            val cmd = session.exec("killall radar_sim_server; killall -9 radar_sim_server")
            log.info(IOUtils.readFully(cmd.inputStream).toString())
            cmd.join(5, TimeUnit.SECONDS)
        }


    }

    private fun initSimulator(): Simulator.Iface {
        return try {
            val transport = TSocket(config.string("simulatorIp"), 9090)
            transport.open()

            // create
            wrap(Client(TBinaryProtocol(transport)))
        } catch (x: TException) {
            throw RuntimeException("Unable to connect to simulator HW", x)
        }
    }

    fun uploadClutterFile(
        file: FileSystemFile,
        progressConsumer: (Double, String) -> Unit) {

        stopSimulation()

        sshClient.apply {
            newSCPFileTransfer().apply {
                useCompression()
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

                upload(file, "/var/clutter.bin")
            }
            progressConsumer(1.0, "Done")
        }

    }

    fun uploadTargetsFile(
        file: FileSystemFile,
        progressConsumer: (Double, String) -> Unit) {

        stopSimulation()

        sshClient.apply {
            newSCPFileTransfer().apply {
                useCompression()
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
                upload(file, "/var/targets.bin")
            }
            progressConsumer(1.0, "Done")
        }
    }

    fun toggleMti(enable: Boolean): Boolean {
        return synchronized(simulatorClient) {
            if (enable) {
                simulatorClient.enableMti()
            } else {
                simulatorClient.disableMti()
            }
            simulatorClient.state.isMtiEnabled
        }
    }

    fun toggleNorm(enable: Boolean): Boolean {
        return synchronized(simulatorClient) {
            if (enable) {
                simulatorClient.enableNorm()
            } else {
                simulatorClient.disableNorm()
            }
            simulatorClient.state.isNormEnabled
        }
    }

    fun startSimulation(
        fromTimeSec: Double,
        progressConsumer: (Double, String) -> Unit) {

        try {

            // calculate the first ARP before the specified time
            val fromArp = floor(fromTimeSec / radarParameters.seekTimeSec).toInt()

            timeShiftFunc.clear()
            acpIdxFunc.clear()

            synchronized(simulatorClient) {
                simulatorClient.apply {
                    disable()
                    // load simulation data from the chosen ARP
                    loadMap(fromArp)
                    enable()
                }
            }

            runLater {
                simulationRunningProperty.set(true)
            }

            var state: SimState
            do {

                val t = System.currentTimeMillis().toDouble()
                state = synchronized(simulatorClient) {
                    simulatorClient.state
                }

                timeShiftFunc.addData(t, state.time.toDouble())
                if (state.simAcpIdx > 0) {
                    acpIdxFunc.addData(
                        state.time.toDouble(),
                        state.simAcpIdx.toDouble()
                    )

                    progressConsumer(
                        state.simAcpIdx.toDouble(),
                        "Running simulation"
                    )
                }

                sleep(1000)

            } while (state.isEnabled)

            progressConsumer(
                state.simAcpIdx.toDouble(),
                "Simulation complete"
            )

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

    fun stopSimulation() {

        try {
            synchronized(simulatorClient) {
                simulatorClient.disable()
            }
        } finally {
            runLater {
                simulationRunningProperty.set(false)
            }
        }
    }

    fun calibrate() {

        try {

            val state = synchronized(simulatorClient) {
                simulatorClient.state
            }

            radarParameters = radarParameters.copy(
                seekTimeSec = state.arpUs / S_TO_US,
                azimuthChangePulse = state.acpCnt,
                impulsePeriodUs = state.trigUs.toDouble()
            )

        } catch (te: TTransportException) {

        } catch (te: TException) {
            runLater {
                simulationRunningProperty.set(false)
            }
            throw te
        }


    }
}

