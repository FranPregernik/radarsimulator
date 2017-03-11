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

                                    newScenario.copy(designerController.scenario)
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

                                    simulationController.stopSimulation()

                                    simulationController.calibrate().apply {
                                        designerController.radarParameters.seekTimeSec = first
                                        designerController.radarParameters.azimuthChangePulse = second
                                        designerController.radarParameters.impulsePeriodUs = third
                                    }

                                    // TODO: move into SimulatorController.uploadClutterFile
                                    FileOutputStream("clutter.bin.gz").use { fileOutputStream ->
                                        GZIPOutputStream(fileOutputStream).use { stream ->
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt((designerController.radarParameters.seekTimeSec * S_TO_US).toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(designerController.radarParameters.azimuthChangePulse.toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(designerController.radarParameters.impulsePeriodUs.toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(designerController.radarParameters.maxImpulsePeriodUs.toInt())
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
                                            val clutterHits = designerController.calculateClutterHits()
                                            clutterHits.writeTo(stream)

                                            // DEBUG
                                            ImageIO.write(
                                                SwingFXUtils.fromFXImage(generateRadarHitImage(clutterHits, designerController.radarParameters), null),
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
                                                    .putInt((designerController.radarParameters.seekTimeSec * S_TO_US).toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(designerController.radarParameters.azimuthChangePulse.toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(designerController.radarParameters.impulsePeriodUs.toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt(designerController.radarParameters.maxImpulsePeriodUs.toInt())
                                                    .array()
                                            )
                                            stream.write(
                                                ByteBuffer.allocate(4)
                                                    .order(ByteOrder.LITTLE_ENDIAN)
                                                    .putInt((designerController.scenario.simulationDurationMin * MIN_TO_S / designerController.radarParameters.seekTimeSec).toInt())
                                                    .array()
                                            )

                                            val mergedHits = Bits(0)

                                            updateMessage("Writing target sim")
                                            updateProgress(0.0, 1.0)

                                            var seekTime = 0.0
                                            designerController.calculateTargetHits().forEach {
                                                seekTime += designerController.radarParameters.seekTimeSec
                                                updateProgress(
                                                    seekTime / (designerController.scenario.simulationDurationMin * MIN_TO_S),
                                                    1.0
                                                )
                                                mergedHits.or(it)
                                                it.writeTo(stream)
                                            }

                                            // DEBUG
                                            ImageIO.write(
                                                SwingFXUtils.fromFXImage(generateRadarHitImage(mergedHits, designerController.radarParameters), null),
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

                            simulationController.currentTimeSecProperty.addListener { _, _, newValue ->
                                val simCurrentTimeSec = newValue.toDouble()
                                if (simCurrentTimeSec.isNaN()) {
                                    return@addListener
                                }

                                if (simCurrentTimeSec <= designerController.scenario.simulationDurationMin * MIN_TO_S) {
                                    designerController.displayParameters.simulatedCurrentTimeSec = simCurrentTimeSec
                                }
                            }

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
                                        isSelected = true
                                        setOnAction {
                                            designerController.displayParameters.distanceUnit = Km
                                            radarScreen.draw()
                                        }
                                    }
                                    togglebutton(NM.toString()) {
                                        setOnAction {
                                            designerController.displayParameters.distanceUnit = NM
                                            radarScreen.draw()
                                        }
                                    }
                                }

                                togglegroup {
                                    togglebutton("0").setOnAction {
                                        designerController.displayParameters.distanceStep = 0.0
                                        radarScreen.draw()
                                    }
                                    togglebutton("10").setOnAction {
                                        designerController.displayParameters.distanceStep = 10.0
                                        radarScreen.draw()
                                    }
                                    togglebutton("50") {
                                        isSelected = true
                                        setOnAction {
                                            designerController.displayParameters.distanceStep = 50.0
                                            radarScreen.draw()
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
                                        isSelected = true
                                        setOnAction {
                                            designerController.displayParameters.azimuthMarkerType = FULL
                                            radarScreen.draw()
                                        }
                                    }
                                    togglebutton(MIN.toString()) {
                                        setOnAction {
                                            designerController.displayParameters.azimuthMarkerType = MIN
                                            radarScreen.draw()
                                        }
                                    }
                                }

                                togglegroup {
                                    togglebutton("0").setOnAction {
                                        designerController.displayParameters.azimuthSteps = 0
                                        radarScreen.draw()
                                    }
                                    togglebutton("5").setOnAction {
                                        designerController.displayParameters.azimuthSteps = 72
                                        radarScreen.draw()
                                    }
                                    togglebutton("10") {
                                        isSelected = true
                                        setOnAction {
                                            designerController.displayParameters.azimuthSteps = 36
                                            radarScreen.draw()
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
                                    bind(radarScreen.movingTargetsLayerOpacityProperty)
                                }
                            }

                            field("Target hit layer") {

                                slider {
                                    tooltip("Controls transparency of the target hit layer")

                                    min = 0.0
                                    max = 1.0
                                    blockIncrement = 0.1
                                    bind(radarScreen.movingHitsLayerOpacityProperty)
                                }
                            }

                            field("Clutter layer") {

                                slider {
                                    tooltip("Controls transparency of the clutter layer")

                                    min = 0.0
                                    max = 1.0
                                    blockIncrement = 0.1
                                    bind(radarScreen.stationaryTargetLayerOpacityProperty)
                                }
                            }

                            field("Clutter map") {
                                button("", fontAwesome.create(FILE_PHOTO_ALT)) {
                                    setOnAction {
                                        val file = chooseFile("Select clutter map", arrayOf(FileChooser.ExtensionFilter("Image  files", "*.jpg")), FileChooserMode.Single)
                                            .firstOrNull()

                                        if (file != null) {
                                            designerController.scenario.clutter = Clutter(file)
                                            radarScreen.draw()
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
                                        designerController.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(0.0)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    setOnAction {
                                        designerController.displayParameters.simulatedCurrentTimeSec = 0.0
                                    }
                                }
                                button("", fontAwesome.create(FAST_BACKWARD)) {
                                    disableProperty().bind(
                                        designerController.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(0.0)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    minWidth = Font.getDefault().size * 3
                                    setOnAction {
                                        val simulatedCurrentTimeSec = designerController.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec - 10 * designerController.radarParameters.seekTimeSec
                                        designerController.displayParameters.simulatedCurrentTimeSec = Math.max(newTime, 0.0)
                                    }
                                }
                                button("", fontAwesome.create(BACKWARD)) {
                                    disableProperty().bind(
                                        designerController.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(0.0)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    setOnAction {
                                        val simulatedCurrentTimeSec = designerController.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec - designerController.radarParameters.seekTimeSec
                                        designerController.displayParameters.simulatedCurrentTimeSec = Math.max(newTime, 0.0)
                                    }
                                }

                                textfield {
                                    disableProperty().bind(simulationController.simulationRunningProperty)
                                    textProperty().bindBidirectional(designerController.displayParameters.simulatedCurrentTimeSecProperty(), SecondsStringConverter())
                                    minWidth = Font.getDefault().size * 3
                                    maxWidth = Font.getDefault().size * 5
                                    prefWidth = Font.getDefault().size * 4
                                    alignment = Pos.BASELINE_CENTER
                                }

                                button("", fontAwesome.create(FORWARD)) {
                                    disableProperty().bind(
                                        designerController.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(designerController.scenario.simulationDurationMin * MIN_TO_S)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    setOnAction {
                                        val simulatedCurrentTimeSec = designerController.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec + designerController.radarParameters.seekTimeSec
                                        designerController.displayParameters.simulatedCurrentTimeSec = Math.min(newTime, designerController.scenario.simulationDurationMin * MIN_TO_S)
                                    }
                                }
                                button("", fontAwesome.create(FAST_FORWARD)) {
                                    disableProperty().bind(
                                        designerController.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(designerController.scenario.simulationDurationMin * MIN_TO_S)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    minWidth = Font.getDefault().size * 3
                                    setOnAction {
                                        val simulatedCurrentTimeSec = designerController.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec + 10 * designerController.radarParameters.seekTimeSec
                                        designerController.displayParameters.simulatedCurrentTimeSec = Math.min(newTime, designerController.scenario.simulationDurationMin * MIN_TO_S)
                                    }
                                }
                                button("", fontAwesome.create(STEP_FORWARD)) {
                                    disableProperty().bind(
                                        designerController.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(designerController.scenario.simulationDurationMin * MIN_TO_S)
                                            .or(simulationController.simulationRunningProperty)
                                    )
                                    setOnAction {
                                        designerController.displayParameters.simulatedCurrentTimeSec = designerController.scenario.simulationDurationMin * MIN_TO_S
                                    }
                                }

                            }
                        }
                    }

                })

                titledpane("Moving targets", vbox {
                    padding = Insets.EMPTY
                    this += movingTargetSelector.root
                    this += movingTargetEditor.root
                    autosize()
                })

            }


        }

    }
}
