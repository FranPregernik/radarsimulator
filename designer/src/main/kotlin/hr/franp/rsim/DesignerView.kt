package hr.franp.rsim

import hr.franp.rsim.models.*
import hr.franp.rsim.models.AzimuthMarkerType.*
import hr.franp.rsim.models.DistanceUnit.*
import javafx.beans.property.*
import javafx.geometry.*
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.scene.text.*
import javafx.stage.*
import net.schmizz.sshj.xfer.*
import org.apache.commons.io.*
import org.controlsfx.glyphfont.*
import org.controlsfx.glyphfont.FontAwesome.Glyph.*
import tornadofx.*
import java.io.*
import java.lang.Math.*
import java.nio.*
import java.nio.channels.FileChannel.MapMode.*
import java.nio.file.*
import java.util.zip.*
import javax.json.Json.*

class DesignerView : View() {
    override val root = BorderPane()
    val status: TaskStatus by inject()

    private var fontAwesome = GlyphFontRegistry.font("FontAwesome")

    private val designerController: DesignerController by inject()
    private val simulationController: SimulatorController by inject()

    private val radarScreen: RadarScreenView by inject()
    private val movingTargetEditor: MovingTargetEditorView by inject()

    val calculatingHitsProperty = SimpleBooleanProperty(false)

    init {

        with(root) {

            maxWidth = 500.0
            minWidth = 300.0

            // add and ensure radar screen fills the space
            center = radarScreen.root.apply {

            }

            right = vbox {

                /**
                 * Display of background task progress
                 */
                hbox(4.0) {

                    paddingAll = 4

                    button("", fontAwesome.create(FOLDER_OPEN)) {
                        disableProperty().bind(
                            calculatingHitsProperty
                                .or(simulationController.simulationRunningProperty)
                        )

                        tooltip("Open scenario")

                        setOnAction {
                            val file = chooseFile("Select simulation scenario file", arrayOf(FileChooser.ExtensionFilter("Simulation scenario file", "*.rsim")), FileChooserMode.Single)
                                .firstOrNull()

                            file?.bufferedReader()?.use { fileBufferReader ->
                                createReader(fileBufferReader)?.use { jsonReader ->
                                    val newScenario = Scenario()
                                    newScenario.updateModel(jsonReader.readObject())
                                    designerController.scenario = newScenario
                                }
                            }
                        }
                    }

                    button("", fontAwesome.create(FLOPPY_ALT)) {
                        disableProperty().bind(
                            calculatingHitsProperty
                                .or(simulationController.simulationRunningProperty)
                        )

                        tooltip("Save scenario")

                        setOnAction {
                            val file = chooseFile("Select simulation scenario file", arrayOf(FileChooser.ExtensionFilter("Simulation scenario file", "*.rsim")), FileChooserMode.Save)
                                .firstOrNull()

                            file?.bufferedWriter()?.use { fileBufferWriter ->
                                createWriter(fileBufferWriter)?.use { jsonWriter ->
                                    jsonWriter.writeObject(designerController.scenario.toJSON())
                                }
                            }
                        }
                    }

                    button("", fontAwesome.create(COGS)) {
                        disableProperty().bind(
                            calculatingHitsProperty
                                .or(simulationController.simulationRunningProperty)
                        )

                        tooltip("Compute scenario")

                        setOnAction {
                            calculatingHitsProperty.set(true)

                            runAsync {
                                try {

                                    updateMessage("Calibrating")
                                    updateProgress(0.0, 1.0)
                                    simulationController.calibrate()
                                    updateProgress(1.0, 1.0)
                                    updateMessage("Calibrated")

                                    val radarParameters = simulationController.radarParameters
                                    val cParams = CalculationParameters(radarParameters)

                                    // ensure dir where we can store the files
                                    File("tmp").mkdir()

                                    // prepare simulation
                                    updateMessage("Writing clutter sim")
                                    updateProgress(0.0, 1.0)
                                    RandomAccessFile(Paths.get("tmp", "clutter.bin").toFile(), "rw").use { raf ->
                                        val fileSizeBytes = FILE_HEADER_BYTE_CNT + 1 * cParams.arpByteCnt
                                        raf.setLength(fileSizeBytes)
                                        raf.channel.use { channel ->
                                            val mappedBuffer = channel.map(READ_WRITE, 0, channel.size())
                                                .order(ByteOrder.LITTLE_ENDIAN)

                                            mappedBuffer.writeHitsHeader(
                                                radarParameters,
                                                (designerController.scenario.simulationDurationMin * MIN_TO_S / radarParameters.seekTimeSec).toInt()
                                            )

                                            designerController.calculateClutterHits(mappedBuffer, true)
                                        }
                                    }
                                    updateMessage("Wrote clutter sim")
                                    updateProgress(1.0, 1.0)

                                    // compress
                                    updateMessage("Zipping clutter sim")
                                    updateProgress(0.0, 1.0)
                                    FileOutputStream("tmp/clutter.bin.gz").use { fileOutputStream ->
                                        GZIPOutputStream(fileOutputStream).use { gzipOutputStream ->
                                            FileInputStream("tmp/clutter.bin").use { fileInputStream ->
                                                IOUtils.copy(fileInputStream, gzipOutputStream)
                                            }
                                        }
                                    }
                                    updateMessage("Zipped clutter sim")
                                    updateProgress(1.0, 1.0)


                                    // prepare targets sim
                                    updateMessage("Writing target sim")
                                    updateProgress(0.0, 1.0)
                                    RandomAccessFile(Paths.get("tmp", "targets.bin").toFile(), "rw").use { raf ->

                                        // ensure number of rotations is decreased so the total file size bytes is not greater than Integer.MAX_VALUE
                                        // because the mmap function does not allow more than that
                                        val rotations = min(
                                            (designerController.scenario.simulationDurationMin * MIN_TO_S / radarParameters.seekTimeSec).toLong(),
                                            (Integer.MAX_VALUE - FILE_HEADER_BYTE_CNT) / cParams.arpByteCnt
                                        )
                                        val fileSizeBytes = (FILE_HEADER_BYTE_CNT + rotations * cParams.arpByteCnt)
                                        raf.setLength(fileSizeBytes)
                                        raf.channel.use { channel ->
                                            val mappedBuffer = channel.map(READ_WRITE, 0, channel.size())
                                                .order(ByteOrder.LITTLE_ENDIAN)

                                            mappedBuffer.writeHitsHeader(
                                                radarParameters,
                                                (designerController.scenario.simulationDurationMin * MIN_TO_S / radarParameters.seekTimeSec).toInt()
                                            )

                                            designerController.calculateTargetHits(mappedBuffer, true)
                                        }
                                    }
                                    updateMessage("Wrote target sim")
                                    updateProgress(1.0, 1.0)


                                    // compress
                                    updateMessage("Zipping target sim")
                                    updateProgress(0.0, 1.0)
                                    FileOutputStream("tmp/targets.bin.gz").use { fileOutputStream ->
                                        GZIPOutputStream(fileOutputStream).use { gzipOutputStream ->
                                            FileInputStream("tmp/targets.bin").use { fileInputStream ->
                                                IOUtils.copy(fileInputStream, gzipOutputStream)
                                            }
                                        }
                                    }
                                    updateMessage("Zipped target sim")
                                    updateProgress(1.0, 1.0)


                                    simulationController.uploadClutterFile(
                                        FileSystemFile("tmp/clutter.bin.gz"),
                                        { progress, _ ->
                                            updateMessage("Sending clutter sim")
                                            updateProgress(progress, 1.0)
                                        }
                                    )
                                    simulationController.uploadTargetsFile(
                                        FileSystemFile("tmp/targets.bin.gz"),
                                        { progress, _ ->
                                            updateMessage("Sending targets sim")
                                            updateProgress(progress, 1.0)
                                        }
                                    )

                                } finally {
                                    calculatingHitsProperty.set(false)
                                }
                            }
                        }
                    }

                    button("", fontAwesome.create(PLAY)) {
                        disableProperty().bind(
                            simulationController.simulationRunningProperty
                                .or(calculatingHitsProperty)
                        )

                        tooltip("Begin simulation")

                        setOnAction {
                            runAsync {
                                simulationController.startSimulation({ progress, message ->
                                    updateMessage(message)
                                    updateProgress(progress, 1.0)
                                })
                            }
                        }
                    }

                    button("", fontAwesome.create(STOP)) {
                        disableProperty().bind(simulationController.simulationRunningProperty.not())

                        tooltip("Stop simulation")

                        setOnAction {
                            simulationController.stopSimulation()
                        }
                    }

                    progressbar(status.progress).apply {
                        visibleWhen { status.running }
                    }

                    label(status.message).apply {
                        visibleWhen { status.running }
                    }

                }

                titledpane("Control", pane {

                    form {
                        fieldset {
                            field {
                                label("Distance") {
                                    tooltip("Distance marker display type")
                                }

                                togglegroup {
                                    togglebutton(Km.toString()) {
                                        isSelected = radarScreen.displayParameters.distanceUnit == Km
                                        setOnAction {
                                            radarScreen.configDistanceDisplay(
                                                Km,
                                                radarScreen.displayParameters.distanceStep
                                            )
                                        }
                                    }
                                    togglebutton(NM.toString()) {
                                        isSelected = radarScreen.displayParameters.distanceUnit == NM
                                        setOnAction {
                                            radarScreen.configDistanceDisplay(
                                                NM,
                                                radarScreen.displayParameters.distanceStep
                                            )
                                        }
                                    }
                                }

                                togglegroup {
                                    togglebutton("0") {
                                        isSelected = radarScreen.displayParameters.distanceStep == 0.0
                                        setOnAction {
                                            radarScreen.configDistanceDisplay(
                                                radarScreen.displayParameters.distanceUnit,
                                                0.0
                                            )
                                        }
                                    }
                                    togglebutton("10") {
                                        isSelected = radarScreen.displayParameters.distanceStep == 10.0
                                        setOnAction {
                                            radarScreen.configDistanceDisplay(
                                                radarScreen.displayParameters.distanceUnit,
                                                10.0
                                            )
                                        }
                                    }
                                    togglebutton("50") {
                                        isSelected = radarScreen.displayParameters.distanceStep == 50.0
                                        setOnAction {
                                            radarScreen.configDistanceDisplay(
                                                radarScreen.displayParameters.distanceUnit,
                                                50.0
                                            )
                                        }
                                    }
                                }
                            }

                            field {

                                label("Azimuth") {
                                    tooltip("Angle marker display type")
                                }

                                togglegroup {
                                    togglebutton(FULL.toString()) {
                                        isSelected = radarScreen.displayParameters.azimuthMarkerType == FULL
                                        setOnAction {
                                            radarScreen.configAzimuthDisplay(
                                                FULL,
                                                radarScreen.displayParameters.azimuthSteps
                                            )
                                        }
                                    }
                                    togglebutton(MIN.toString()) {
                                        isSelected = radarScreen.displayParameters.azimuthMarkerType == MIN
                                        setOnAction {
                                            radarScreen.configAzimuthDisplay(
                                                MIN,
                                                radarScreen.displayParameters.azimuthSteps
                                            )
                                        }
                                    }
                                }

                                togglegroup {
                                    togglebutton("0") {
                                        isSelected = radarScreen.displayParameters.azimuthSteps == 0
                                        setOnAction {
                                            radarScreen.configAzimuthDisplay(
                                                radarScreen.displayParameters.azimuthMarkerType,
                                                0
                                            )
                                        }
                                    }
                                    togglebutton("5") {
                                        isSelected = radarScreen.displayParameters.azimuthSteps == 18
                                        setOnAction {
                                            radarScreen.configAzimuthDisplay(
                                                radarScreen.displayParameters.azimuthMarkerType,
                                                18
                                            )
                                        }
                                    }
                                    togglebutton("10") {
                                        isSelected = radarScreen.displayParameters.azimuthSteps == 36
                                        setOnAction {
                                            radarScreen.configAzimuthDisplay(
                                                radarScreen.displayParameters.azimuthMarkerType,
                                                36
                                            )
                                        }
                                    }
                                }

                            }

                            field("Target layer") {

                                slider {
                                    tooltip("Controls transparency of the target layer")

                                    prefWidth = 200.0

                                    min = 0.0
                                    max = 1.0
                                    blockIncrement = 0.1

                                    value = radarScreen.displayParameters.targetLayerOpacity

                                    valueProperty().addListener { _, _, newValue ->
                                        radarScreen.configTargetLayerOpacity(newValue.toDouble())
                                    }
                                }
                            }

                            field("Target hit layer") {
                                hbox(4.0) {
                                    val mtiSlider = slider {
                                        tooltip("Controls transparency of the target hit layer")

                                        prefWidth = 200.0

                                        min = 0.0
                                        max = 1.0
                                        blockIncrement = 0.1

                                        value = radarScreen.displayParameters.targetHitLayerOpacity
                                        valueProperty().addListener { _, _, newValue ->
                                            radarScreen.configTargetHitLayerOpacity(newValue.toDouble())
                                        }
                                    }
                                    this += mtiSlider
                                    checkbox {
                                        disableProperty().bind(
                                            simulationController.simulationRunningProperty
                                        )

                                        tooltip("MTI")
                                        selectedProperty().set(true)
                                        setOnAction {
                                            // TODO: set MTI on HW
                                            mtiSlider.value = if (selectedProperty().get())
                                                1.0
                                            else
                                                0.0
                                        }
                                    }
                                }
                            }
                            field("Target plot history") {
                                val slider = slider {
                                    tooltip("Number of previous hits to display")
                                    prefWidth = 200.0

                                    min = 0.0
                                    max = 6.0
                                    blockIncrement = 1.0

                                    value = radarScreen.displayParameters.plotHistoryCount.toDouble()
                                    valueProperty().addListener { _, _, newValue ->
                                        radarScreen.configPlotHistory(newValue.toInt())
                                    }
                                }
                                this += slider
                                label {
                                    textProperty().bind(slider.valueProperty().asString("%.0f"))
                                }
                            }

                            field("Clutter layer") {
                                hbox(4.0) {
                                    val normSlider = slider {
                                        tooltip("Controls transparency of the clutter layer")
                                        prefWidth = 200.0

                                        min = 0.0
                                        max = 1.0
                                        blockIncrement = 0.1

                                        value = radarScreen.displayParameters.clutterLayerOpacity
                                        valueProperty().addListener { _, _, newValue ->
                                            radarScreen.configClutterLayerOpacity(newValue.toDouble())
                                        }
                                    }
                                    this += normSlider
                                    checkbox {
                                        disableProperty().bind(
                                            simulationController.simulationRunningProperty
                                        )

                                        tooltip("NORM")
                                        selectedProperty().set(true)
                                        setOnAction {
                                            // TODO: set NORM on HW
                                            normSlider.value = if (selectedProperty().get())
                                                1.0
                                            else
                                                0.0
                                        }
                                    }
                                }
                            }

                            field("Clutter map") {
                                button("", fontAwesome.create(FILE_PHOTO_ALT)) {
                                    setOnAction {
                                        val file = chooseFile("Select clutter map", arrayOf(FileChooser.ExtensionFilter("Image  files", "*.jpg", "*.png")), FileChooserMode.Single)
                                            .firstOrNull()

                                        if (file != null) {
                                            designerController.scenario.clutter = Clutter(file)
                                            this.tooltip = Tooltip("Select clutter map")
                                        }
                                    }
                                }

                            }
                        }
                        fieldset {
                            field("Tsim") {

                                button("", fontAwesome.create(STEP_BACKWARD)) {
                                    disableProperty().bind(
                                        radarScreen.simulatedCurrentTimeSecProperty.isEqualTo(0.0, 0.001)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    setOnAction {
                                        radarScreen.simulatedCurrentTimeSecProperty.set(0.0)
                                    }
                                }
                                button("", fontAwesome.create(FAST_BACKWARD)) {
                                    disableProperty().bind(
                                        radarScreen.simulatedCurrentTimeSecProperty.isEqualTo(0.0, 0.001)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    minWidth = Font.getDefault().size * 3
                                    setOnAction {
                                        val simulatedCurrentTimeSec = radarScreen.simulatedCurrentTimeSecProperty.get()
                                        val newTime = simulatedCurrentTimeSec - 10 * simulationController.radarParameters.seekTimeSec
                                        radarScreen.simulatedCurrentTimeSecProperty.set(max(newTime, 0.0))
                                    }
                                }
                                button("", fontAwesome.create(BACKWARD)) {
                                    disableProperty().bind(
                                        radarScreen.simulatedCurrentTimeSecProperty.isEqualTo(0.0, 0.001)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    setOnAction {
                                        val simulatedCurrentTimeSec = radarScreen.simulatedCurrentTimeSecProperty.get()
                                        val newTime = simulatedCurrentTimeSec - simulationController.radarParameters.seekTimeSec
                                        radarScreen.simulatedCurrentTimeSecProperty.set(max(newTime, 0.0))
                                    }
                                }

                                button("", fontAwesome.create(FORWARD)) {
                                    disableProperty().bind(
                                        radarScreen.simulatedCurrentTimeSecProperty.isEqualTo(designerController.scenario.simulationDurationMin * MIN_TO_S, 0.001)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    setOnAction {
                                        val simulatedCurrentTimeSec = radarScreen.simulatedCurrentTimeSecProperty.get()
                                        val newTime = simulatedCurrentTimeSec + simulationController.radarParameters.seekTimeSec
                                        radarScreen.simulatedCurrentTimeSecProperty.set(min(newTime, designerController.scenario.simulationDurationMin * MIN_TO_S))
                                    }
                                }
                                button("", fontAwesome.create(FAST_FORWARD)) {
                                    disableProperty().bind(
                                        radarScreen.simulatedCurrentTimeSecProperty.isEqualTo(designerController.scenario.simulationDurationMin * MIN_TO_S, 0.001)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    minWidth = Font.getDefault().size * 3
                                    setOnAction {
                                        val simulatedCurrentTimeSec = radarScreen.simulatedCurrentTimeSecProperty.get()
                                        val newTime = simulatedCurrentTimeSec + 10 * simulationController.radarParameters.seekTimeSec
                                        radarScreen.simulatedCurrentTimeSecProperty.set(min(newTime, designerController.scenario.simulationDurationMin * MIN_TO_S))
                                    }
                                }
                                button("", fontAwesome.create(STEP_FORWARD)) {
                                    disableProperty().bind(
                                        radarScreen.simulatedCurrentTimeSecProperty.isEqualTo(designerController.scenario.simulationDurationMin * MIN_TO_S, 0.001)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    setOnAction {
                                        radarScreen.simulatedCurrentTimeSecProperty.set(designerController.scenario.simulationDurationMin * MIN_TO_S)
                                    }
                                }
                            }
                        }
                    }

                })

                titledpane("Radar targets", vbox {

                    disableProperty().bind(
                        calculatingHitsProperty.or(
                            simulationController.simulationRunningProperty
                        )
                    )

                    padding = Insets.EMPTY
                    this += movingTargetEditor.root
                    autosize()
                })

            }


        }

    }
}
