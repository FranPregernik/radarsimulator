package hr.franp.rsim

import org.apache.thrift.server.*
import org.apache.thrift.transport.*
import java.lang.Math.*
import java.lang.Thread.*
import kotlin.concurrent.*


val handler = SimulatorMock()

val processor = Simulator.Processor(handler)

fun main(args: Array<String>) {

    try {

        val simple = Runnable {
            try {
                val serverTransport = TServerSocket(9090)
                val server = TSimpleServer(TServer.Args(serverTransport).processor(processor))
                println("Starting the simple server...")
                server.serve()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        Thread(simple).start()
    } catch (x: Exception) {
        x.printStackTrace()
    }

}


class SimulatorMock : Simulator.Iface, AutoCloseable {

    private val simState = SimState()

    init {
        simState.arpUs = 12_000_000
        simState.acpCnt = 8_196
        simState.trigUs = 3_003
    }

    private val mockedSim = thread {
        while (true) {

            val arpNs = max(simState.arpUs, 12_000_000) * 1000L
            val acpNs = arpNs / max(simState.acpCnt, 8196)
            sleep(
                (acpNs / 1e6).toLong(),
                (acpNs % 1e6).toInt()
            )

            if (simState.isEnabled) {
                simState.time = (System.currentTimeMillis() / 1000L).toInt()
                simState.simAcpIdx++
            }
        }
    }

    override fun close() {
        mockedSim.interrupt()
        mockedSim.join()
    }

    override fun reset() {
        simState.clear()
        simState.arpUs = 12_000_000
        simState.acpCnt = 8_196
        simState.trigUs = 3_003
    }

    override fun calibrate() {
        sleep(5000)
        simState.isCalibrated = true
    }

    override fun enable() {
        simState.isEnabled = true
    }

    override fun enableMti() {
        simState.isMtiEnabled = true
    }

    override fun enableNorm() {
        simState.isNormEnabled = true
    }

    override fun disable() {
        simState.isEnabled = false
    }

    override fun disableMti() {
        simState.isMtiEnabled = false
    }

    override fun disableNorm() {
        simState.isNormEnabled = false
    }

    override fun loadMap(arpPosition: Int) {
        // noop
    }

    override fun getState() = simState

}
