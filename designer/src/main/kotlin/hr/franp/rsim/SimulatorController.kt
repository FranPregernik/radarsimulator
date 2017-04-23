package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.application.Platform.*
import javafx.beans.property.*
import net.schmizz.sshj.*
import net.schmizz.sshj.common.*
import net.schmizz.sshj.xfer.*
import org.apache.commons.math3.stat.regression.*
import org.apache.thrift.*
import org.apache.thrift.protocol.*
import org.apache.thrift.transport.*
import tornadofx.*
import java.lang.Math.*
import java.lang.Thread.*

class SimulatorController : Controller(), AutoCloseable {

    val transport: TTransport
    val simulatorClient: Simulator.Client

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

    private val sshClient = SSHClient().apply {
        // no need to verify, not really security oriented
        addHostKeyVerifier { _, _, _ -> true }
        useCompression()
    }

    val simulationRunningProperty = SimpleBooleanProperty(false)

    init {
        try {
            transport = TSocket(config.string("simulatorIp"), 9090)
            //transport = TSocket(config.string("simulatorIp"), 9090)
            transport.open()

            simulatorClient = Simulator.Client(
                TBinaryProtocol(transport)
            )
            simulatorClient.reset()

        } catch (x: TException) {
            throw RuntimeException("Unable to connect to simulator HW", x)
        }

        sshClient.apply {

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

        }

    }

    override fun close() {
        transport.close()
        sshClient.close()
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

    fun enableMti() = simulatorClient.enableMti()

    fun enableNorm() = simulatorClient.enableMti()

    fun startSimulation(
        fromTimeSec: Double,
        progressConsumer: (Double, String) -> Unit) {

        try {

            calibrate()

            // calculate the first ARP before the specified time
            val fromArp = floor(fromTimeSec / radarParameters.seekTimeSec).toInt()

            timeShiftFunc.clear()
            acpIdxFunc.clear()

            simulatorClient.apply {

                // load simulation data from the chosen ARP
                loadClutterMap(fromArp)
                loadTargetMap(fromArp)

                enable()
            }

            runLater {
                simulationRunningProperty.set(true)
            }

            var state: SimState
            do {

                val t = System.currentTimeMillis().toDouble()
                state = simulatorClient.state

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
            simulatorClient.disable()
        } finally {
            runLater {
                simulationRunningProperty.set(false)
            }
        }
    }

    fun calibrate() {

        try {

            simulatorClient.calibrate()

            val state = simulatorClient.state
            radarParameters = radarParameters.copy(
                seekTimeSec = state.arpUs / S_TO_US,
                azimuthChangePulse = state.acpCnt,
                impulsePeriodUs = state.trigUs.toDouble()
            )

        } catch (te: TException) {
            runLater {
                simulationRunningProperty.set(false)
            }
            throw te
        }


    }
}

