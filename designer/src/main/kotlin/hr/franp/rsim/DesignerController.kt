package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.beans.property.*
import javafx.scene.image.*
import javafx.stage.*
import net.schmizz.sshj.xfer.*
import org.apache.commons.io.*
import tornadofx.*
import java.io.*
import java.lang.Math.*
import java.nio.*
import java.nio.channels.*
import java.util.zip.*
import javax.imageio.*
import javax.json.*

class DesignerController() : Controller() {

    val cloudOneImage = processHitMaskImage(Image(resources["/cloud1.png"]))
    val cloudTwoImage = processHitMaskImage(Image(resources["/cloud2.png"]))

    private val simulationController: SimulatorController by inject()

    var scenario by property(Scenario().apply {
        simulationDurationMin = 120.0
        simulationStepUs = 100000.0
    })

    val scenarioProperty = getProperty(DesignerController::scenario)

    var scenarioFile by property<File>(null)
    val scenarioFileProperty = getProperty(DesignerController::scenarioFile)

    /**
     * Selected moving target name
     */
    var selectedMovingTarget by property<MovingTarget>(null)
    val selectedMovingTargetProperty = getProperty(DesignerController::selectedMovingTarget)

    val calculatingHitsProperty = SimpleBooleanProperty(false)

    /**
     * @return A stream of pairs(acp, us)
     */
    fun calculateTargetHits(buff: ByteBuffer,
                            fromTimeSec: Double = 0.0,
                            toTimeSec: Double = scenario.simulationDurationMin * MIN_TO_S,
                            compress: Boolean = false) {

        val radarParameters = simulationController.radarParameters
        val cParams = CalculationParameters(radarParameters)

        if (scenario.movingTargets.isEmpty()) {
            return
        }

        val simulationDurationMin = scenario.simulationDurationMin
        val targetPathSegments = scenario.getAllPathSegments()

        // iterate over time
        generateSequence(fromTimeSec) { it + radarParameters.seekTimeSec }
            .takeWhile { it < simulationDurationMin * MIN_TO_S }
            .forEach { minTimeSec ->

                val minTimeUs = S_TO_US * minTimeSec
                val maxTimeUs = S_TO_US * min(
                    max(minTimeSec, toTimeSec),
                    minTimeSec + radarParameters.seekTimeSec
                )

                // and all target paths
                targetPathSegments.forEach { pathSegment ->
                    when (pathSegment.type) {
                        MovingTargetType.Point -> buff.calculatePointTargetHits(
                            pathSegment,
                            minTimeUs,
                            maxTimeUs,
                            cParams,
                            compress
                        )
                        MovingTargetType.Test1 -> buff.calculateTest1TargetHits(
                            pathSegment,
                            minTimeUs,
                            maxTimeUs,
                            cParams,
                            compress
                        )
                        MovingTargetType.Test2 -> buff.calculateTest2TargetHits(
                            pathSegment,
                            minTimeUs,
                            maxTimeUs,
                            cParams,
                            compress
                        )
                        else -> {
                            // no op
                            // clouds are not calculated as moving targets
                        }
                    }
                }
            }
    }


    fun calculateClutterHits(buff: ByteBuffer) {

        val radarParameters = simulationController.radarParameters
        val cParams = CalculationParameters(radarParameters)

        // (deep)clone for background processing
        val scenario = this.scenario.copy<Scenario>()

        val maxRadarDistanceKm = radarParameters.maxRadarDistanceKm
        val distanceResolutionKm = radarParameters.distanceResolutionKm

        val width = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()
        val height = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()

        val raster = scenario.clutter?.getImage(width, height)?.getRasterHitMap()
        if (raster != null) {
            buff.calculateClutterMapHits(
                hitRaster = raster,
                cParams = cParams
            )
        }

        // HACK: we are using moving targets as fixed point clutter maps
        scenario.movingTargets
            ?.filter { it.type == MovingTargetType.Cloud1 || it.type == MovingTargetType.Cloud2 }
            ?.forEach {
                when (it.type) {
                    MovingTargetType.Cloud1 -> buff.calculateClutterMapHits(
                        hitRaster = cloudOneImage.getRasterHitMap(),
                        cParams = cParams,
                        origin = it.initialPosition,
                        scale = 1.0
                    )
                    MovingTargetType.Cloud2 -> buff.calculateClutterMapHits(
                        hitRaster = cloudTwoImage.getRasterHitMap(),
                        cParams = cParams,
                        origin = it.initialPosition,
                        scale = 1.0
                    )
                    else -> {
                        // no op
                        // no other moving target is clutter
                    }
                }
            }
    }

    /**
     * Loads a scenario from the specified file and keeps a reference to it.
     */
    fun loadScenario() {
        val file = chooseFile("Select simulation scenario file", arrayOf(FileChooser.ExtensionFilter("Simulation scenario file", "*.rsim")), FileChooserMode.Single)
            .firstOrNull() ?: return

        file.bufferedReader().use { fileBufferReader ->
            Json.createReader(fileBufferReader)?.use { jsonReader ->
                val newScenario = Scenario()
                newScenario.updateModel(jsonReader.readObject())
                scenario = newScenario
                this.scenarioFile = file
            }
        }

    }

    /**
     * Saves the scenario to the file.
     */
    fun saveScenario() {
        val file = chooseFile("Select simulation scenario file", arrayOf(FileChooser.ExtensionFilter("Simulation scenario file", "*.rsim")), FileChooserMode.Save)
            .firstOrNull() ?: return

        file.bufferedWriter().use { fileBufferWriter ->
            Json.createWriter(fileBufferWriter)?.use { jsonWriter ->
                jsonWriter.writeObject(scenario.toJSON())
                this.scenarioFile = file
            }
        }
    }

    /**
     * Clears the scenario.
     */
    fun resetScenario() {
        this.scenario = Scenario().apply {
            simulationDurationMin = 120.0
            simulationStepUs = 100000.0
        }
        this.scenarioFile = null
    }

    fun computeScenario(debug: Boolean = false) {

        calculatingHitsProperty.set(true)

        if (scenarioFile == null) {
            saveScenario()
        }

        if (scenarioFile != null) {

            val clutterBinFile = scenarioFile.resolveSibling(scenarioFile.absoluteFile.nameWithoutExtension + ".clutter.bin")
            val targetsBinFile = scenarioFile.resolveSibling(scenarioFile.absoluteFile.nameWithoutExtension + ".targets.bin")

            // remove previous file
            // TODO: ask for permission
            if (clutterBinFile.exists() || targetsBinFile.exists()) {
                confirm(
                    header = "Existing simulation",
                    content = "Are you sure you want to overwrite the generated simulation files?"
                ) {
                    clutterBinFile.delete()
                    targetsBinFile.delete()
                }
            }

            runAsync {

                updateMessage("Calibrating")
//                                    updateProgress(0.0, 1.0)
                simulationController.calibrate()
//                                    updateProgress(1.0, 1.0)
                updateMessage("Calibrated")

                val radarParameters = simulationController.radarParameters
                val cParams = CalculationParameters(radarParameters)


                // prepare simulation
                updateMessage("Writing clutter sim")
//                                    updateProgress(0.0, 1.0)


                RandomAccessFile(clutterBinFile, "rw").use { raf ->
                    val rotations = 1
                    val FILE_HIT_BYTE_CNT = rotations * cParams.arpByteCnt
                    val fileSizeBytes = FILE_HEADER_BYTE_CNT + FILE_HIT_BYTE_CNT
                    raf.setLength(fileSizeBytes)

                    raf.channel.use { channel ->

                        val buffArray = ByteArray(channel.size().toInt())
                        val buff = ByteBuffer.wrap(buffArray).order(ByteOrder.LITTLE_ENDIAN)

                        // write hits
                        calculateClutterHits(buff = buff)

                        // dump to file
                        val mappedBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size())
                            .order(ByteOrder.LITTLE_ENDIAN)
                        mappedBuffer.writeHitsHeader(
                            radarParameters,
                            (scenario.simulationDurationMin * MIN_TO_S / radarParameters.seekTimeSec).toInt()
                        )

                        buff.spreadHits(mappedBuffer, cParams)

                        // add acp index
                        (0..(rotations * cParams.azimuthChangePulseCount - 1)).forEach {
                            mappedBuffer.putInt(
                                FILE_HEADER_BYTE_CNT + (it * cParams.acpByteCnt).toInt(),
                                it % cParams.azimuthChangePulseCount
                            )
                        }

                    }
                }
                updateMessage("Wrote clutter sim")
//                                    updateProgress(1.0, 1.0)

                if (debug) {
                    updateMessage("Writing clutter projection")
                    RandomAccessFile(clutterBinFile, "r").use { raf ->
                        raf.channel.use { channel ->

                            val mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                                .order(ByteOrder.LITTLE_ENDIAN)

                            ImageIO.write(
                                mappedBuffer.toCompressedHitImage(cParams),
                                "png",
                                clutterBinFile.resolveSibling(clutterBinFile.toString() + ".png")
                            )
                        }
                    }
                    updateMessage("Wrote clutter projection")
                }


                // prepare targets sim
                updateMessage("Writing target sim")
//                                    updateProgress(0.0, 1.0)

                // remove previous file
                RandomAccessFile(targetsBinFile, "rw").use { raf ->

                    // ensure number of rotations is decreased so the total file size bytes is not greater than Integer.MAX_VALUE
                    // because the mmap function does not allow more than that
                    val rotations = min(
                        (scenario.simulationDurationMin * MIN_TO_S / radarParameters.seekTimeSec).toLong(),
                        (Integer.MAX_VALUE - FILE_HEADER_BYTE_CNT) / cParams.arpByteCnt
                    ).toInt()
                    val FILE_HIT_BYTE_CNT = rotations * cParams.arpByteCnt
                    val fileSizeBytes = (FILE_HEADER_BYTE_CNT + FILE_HIT_BYTE_CNT)
                    raf.setLength(fileSizeBytes)

                    raf.channel.use { channel ->

                        val buffArray = ByteArray(channel.size().toInt())
                        val buff = ByteBuffer.wrap(buffArray).order(ByteOrder.LITTLE_ENDIAN)

                        // write hits to memory for speed
                        calculateTargetHits(buff)

                        // dump to file
                        val mappedBuffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, channel.size())
                            .order(ByteOrder.LITTLE_ENDIAN)
                        mappedBuffer.writeHitsHeader(
                            radarParameters,
                            (scenario.simulationDurationMin * MIN_TO_S / radarParameters.seekTimeSec).toInt()
                        )

                        buff.spreadHits(mappedBuffer, cParams)

                        // add acp index
                        (0..(rotations * cParams.azimuthChangePulseCount - 1)).forEach {
                            mappedBuffer.putInt(
                                FILE_HEADER_BYTE_CNT + (it * cParams.acpByteCnt).toInt(),
                                it % cParams.azimuthChangePulseCount
                            )
                        }
                    }
                }
                updateMessage("Wrote target sim")
//                                    updateProgress(1.0, 1.0)

                if (debug) {
                    updateMessage("Writing target projection")
                    RandomAccessFile(targetsBinFile, "r").use { raf ->
                        raf.channel.use { channel ->

                            val mappedBuffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                                .order(ByteOrder.LITTLE_ENDIAN)

                            ImageIO.write(
                                mappedBuffer.toCompressedHitImage(cParams),
                                "png",
                                targetsBinFile.resolveSibling(targetsBinFile.toString() + ".png")
                            )
                        }
                    }
                    updateMessage("Writing target projection")
                }

                // compress
                updateMessage("Zipping clutter sim")
//                                    updateProgress(0.0, 1.0)
                val clutterZipFile = clutterBinFile.resolveSibling(clutterBinFile.toString() + ".gz")
                FileOutputStream(clutterZipFile).use { fileOutputStream ->
                    GZIPOutputStream(fileOutputStream).use { gzipOutputStream ->
                        FileInputStream(clutterBinFile).use { fileInputStream ->
                            IOUtils.copy(fileInputStream, gzipOutputStream)
                        }
                    }
                }
                updateMessage("Zipped clutter sim")
//                                    updateProgress(1.0, 1.0)

                // compress
                updateMessage("Zipping target sim")
//                                    updateProgress(0.0, 1.0)
                val targetsZipFile = targetsBinFile.resolveSibling(targetsBinFile.toString() + ".gz")
                FileOutputStream(targetsZipFile).use { fileOutputStream ->
                    GZIPOutputStream(fileOutputStream).use { gzipOutputStream ->
                        FileInputStream(targetsBinFile).use { fileInputStream ->
                            IOUtils.copy(fileInputStream, gzipOutputStream)
                        }
                    }
                }
                updateMessage("Zipped target sim")
//                                    updateProgress(1.0, 1.0)

                simulationController.uploadClutterFile(
                    FileSystemFile(clutterZipFile),
                    { progress, _ ->
                        updateMessage("Sending clutter sim")
                        updateProgress(progress, 1.0)
                    }
                )
                simulationController.uploadTargetsFile(
                    FileSystemFile(targetsZipFile),
                    { progress, _ ->
                        updateMessage("Sending targets sim")
                        updateProgress(progress, 1.0)
                    }
                )

            } ui {
                calculatingHitsProperty.set(false)
            }

        } else {
            calculatingHitsProperty.set(false)
        }
    }
}
