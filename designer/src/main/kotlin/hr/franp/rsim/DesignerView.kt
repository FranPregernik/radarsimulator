package hr.franp.rsim

import hr.franp.*
import hr.franp.rsim.models.*
import hr.franp.rsim.models.AzimuthMarkerType.*
import hr.franp.rsim.models.DistanceUnit.*
import javafx.beans.property.*
import javafx.embed.swing.*
import javafx.geometry.*
import javafx.scene.control.*
import javafx.scene.image.*
import javafx.scene.layout.*
import javafx.scene.text.*
import javafx.stage.*
import net.schmizz.sshj.xfer.*
import org.controlsfx.glyphfont.*
import org.controlsfx.glyphfont.FontAwesome.Glyph.*
import tornadofx.*
import java.io.*
import java.nio.*
import java.util.zip.*
import javax.imageio.*
import javax.json.Json.*

class DesignerView : View() {
    override val root = BorderPane()
    val status: TaskStatus by inject()

    private var fontAwesome = GlyphFontRegistry.font("FontAwesome")

    private val designerController: DesignerController by inject()
    private val simulationController: SimulatorController by inject()

    private val radarScreen: RadarScreenView by inject()
    private val movingTargetEditor: MovingTargetEditorView by inject()
    private val movingTargetSelector: MovingTargetSelectorView by inject()

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

                                    simulationController.calibrate()
                                    val radarParameters = simulationController.radarParameters

                                    // TODO: move into SimulatorController.uploadClutterFile
                                    FileOutputStream("clutter.bin.gz").use { fileOutputStream ->
                                        GZIPOutputStream(fileOutputStream).use { stream ->
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt((radarParameters.seekTimeSec * S_TO_US).toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(radarParameters.azimuthChangePulse)
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(radarParameters.impulsePeriodUs.toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(radarParameters.maxImpulsePeriodUs.toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(1)
                                                    .array()
                                            )

                                            updateMessage("Writing clutter sim")
                                            updateProgress(0.0, 1.0)

                                            val mergedHits = Bits(0)
                                            var seekTime = 0.0
                                            designerController.calculateClutterHits()
                                                .forEach {
                                                    seekTime += radarParameters.seekTimeSec
                                                    mergedHits.or(it)
                                                    it.writeTo(stream)
                                                }

                                            // DEBUG
                                            ImageIO.write(
                                                SwingFXUtils.fromFXImage(generateRadarHitImage(mergedHits, radarParameters), null),
                                                "png",
                                                File("clutter.png")
                                            )

                                            updateMessage("Wrote clutter sim")
                                            updateProgress(1.0, 1.0)
                                        }
                                    }

                                    // TODO: move into SimulatorController.uploadTargetsFile
                                    FileOutputStream("targets.bin.gz").use { fileOutputStream ->
                                        GZIPOutputStream(fileOutputStream).use { stream ->
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt((radarParameters.seekTimeSec * S_TO_US).toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(radarParameters.azimuthChangePulse)
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(radarParameters.impulsePeriodUs.toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(radarParameters.maxImpulsePeriodUs.toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt((designerController.scenario.simulationDurationMin * MIN_TO_S / radarParameters.seekTimeSec).toInt())
                                                    .array()
                                            )

                                            val mergedHits = Bits(0)

                                            updateMessage("Writing target sim")
                                            updateProgress(0.0, 1.0)

                                            var seekTime = 0.0
                                            designerController.calculateTargetHits()
                                                .forEach {
                                                    seekTime += radarParameters.seekTimeSec
                                                    updateProgress(
                                                        seekTime / (designerController.scenario.simulationDurationMin * MIN_TO_S),
                                                        1.0
                                                    )
                                                    mergedHits.or(it)
                                                    it.writeTo(stream)
                                                }

                                            // DEBUG
                                            ImageIO.write(
                                                SwingFXUtils.fromFXImage(generateRadarHitImage(mergedHits, radarParameters), null),
                                                "png",
                                                File("targets.png")
                                            )

                                            updateMessage("Wrote target sim")
                                            updateProgress(1.0, 1.0)
                                        }
                                    }

                                    simulationController.uploadClutterFile(
                                        FileSystemFile("clutter.bin.gz"),
                                        { progress, message ->
                                            updateMessage(message)
                                            updateProgress(progress, 1.0)
                                        }
                                    )
                                    simulationController.uploadTargetsFile(
                                        FileSystemFile("targets.bin.gz"),
                                        { progress, message ->
                                            updateMessage(message)
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

                                slider {
                                    tooltip("Controls transparency of the target hit layer")

                                    min = 0.0
                                    max = 1.0
                                    blockIncrement = 0.1

                                    value = radarScreen.displayParameters.targetHitLayerOpacity
                                    valueProperty().addListener { _, _, newValue ->
                                        radarScreen.configTargetHitLayerOpacity(newValue.toDouble())
                                    }
                                }
                            }

                            field("Clutter layer") {

                                slider {
                                    tooltip("Controls transparency of the clutter layer")

                                    min = 0.0
                                    max = 1.0
                                    blockIncrement = 0.1

                                    value = radarScreen.displayParameters.clutterLayerOpacity
                                    valueProperty().addListener { _, _, newValue ->
                                        radarScreen.configClutterLayerOpacity(newValue.toDouble())
                                    }
                                }
                            }

                            field("Clutter map") {
                                button("", fontAwesome.create(FILE_PHOTO_ALT)) {
                                    setOnAction {
                                        val file = chooseFile("Select clutter map", arrayOf(FileChooser.ExtensionFilter("Image  files", "*.jpg")), FileChooserMode.Single)
                                            .firstOrNull()

                                        if (file != null) {
                                            designerController.scenario.clutter = Clutter(file)
                                            this.tooltip = Tooltip("Select clutter map").apply {
                                                graphic = ImageView(designerController.scenario.clutter.getImage(100, 100))
                                            }
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
                                        radarScreen.simulatedCurrentTimeSecProperty.set(Math.max(newTime, 0.0))
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
                                        radarScreen.simulatedCurrentTimeSecProperty.set(Math.max(newTime, 0.0))
                                    }
                                }

                                textfield {
                                    disableProperty().bind(simulationController.simulationRunningProperty)
                                    textProperty().bindBidirectional(radarScreen.simulatedCurrentTimeSecProperty, SecondsStringConverter())
                                    minWidth = Font.getDefault().size * 3
                                    maxWidth = Font.getDefault().size * 5
                                    prefWidth = Font.getDefault().size * 4
                                    alignment = Pos.BASELINE_CENTER
                                }

                                button("", fontAwesome.create(FORWARD)) {
                                    disableProperty().bind(
                                        radarScreen.simulatedCurrentTimeSecProperty.isEqualTo(designerController.scenario.simulationDurationMin * MIN_TO_S, 0.001)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    setOnAction {
                                        val simulatedCurrentTimeSec = radarScreen.simulatedCurrentTimeSecProperty.get()
                                        val newTime = simulatedCurrentTimeSec + simulationController.radarParameters.seekTimeSec
                                        radarScreen.simulatedCurrentTimeSecProperty.set(Math.min(newTime, designerController.scenario.simulationDurationMin * MIN_TO_S))
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
                                        radarScreen.simulatedCurrentTimeSecProperty.set(Math.min(newTime, designerController.scenario.simulationDurationMin * MIN_TO_S))
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

                            field("AZ") {

                                label {
                                    val angleConverter = AngleStringConverter()

                                    radarScreen.simulatedCurrentTimeSecProperty.addListener { _, _, newValue ->
                                        textProperty().set(
                                            angleConverter.toString(
                                                normalizeAngleDeg(
                                                    360.0 * (newValue.toDouble() / simulationController.radarParameters.seekTimeSec)
                                                )
                                            )
                                        )
                                    }
                                }

                            }
                        }
                    }

                })

                titledpane("Moving targets", vbox {

                    disableProperty().bind(
                        calculatingHitsProperty.or(
                            simulationController.simulationRunningProperty
                        )
                    )

                    padding = Insets.EMPTY
                    this += movingTargetSelector.root
                    this += movingTargetEditor.root
                    autosize()
                })

            }


        }

    }
}
