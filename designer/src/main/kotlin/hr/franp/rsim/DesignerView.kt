package hr.franp.rsim

import hr.franp.rsim.models.*
import hr.franp.rsim.models.AzimuthMarkerType.*
import hr.franp.rsim.models.DistanceUnit.*
import javafx.geometry.*
import javafx.scene.input.*
import javafx.scene.layout.*
import javafx.scene.text.*
import javafx.stage.*
import org.controlsfx.glyphfont.*
import org.controlsfx.glyphfont.FontAwesome.Glyph.*
import tornadofx.*
import java.lang.Math.*

class DesignerView : View() {
    override val root = BorderPane()
    val status: TaskStatus by inject()

    private var fontAwesome = GlyphFontRegistry.font("FontAwesome")

    private val designerController: DesignerController by inject()
    private val simulationController: SimulatorController by inject()

    private val radarScreen: RadarScreenView by inject()
    private val movingTargetEditor: MovingTargetEditorView by inject()

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

                    button("", fontAwesome.create(ERASER)) {
                        disableProperty().bind(
                            designerController.calculatingHitsProperty
                                .or(simulationController.simulationRunningProperty)
                        )

                        tooltip("Reset scenario")

                        setOnAction {
                            designerController.resetScenario()
                        }
                    }

                    button("", fontAwesome.create(FOLDER_OPEN)) {
                        disableProperty().bind(
                            designerController.calculatingHitsProperty
                                .or(simulationController.simulationRunningProperty)
                        )

                        tooltip("Open scenario")

                        setOnAction {
                            designerController.loadScenario()
                        }
                    }

                    button("", fontAwesome.create(FLOPPY_ALT)) {
                        disableProperty().bind(
                            designerController.calculatingHitsProperty
                                .or(simulationController.simulationRunningProperty)
                        )

                        tooltip("Save scenario")

                        setOnAction {
                            designerController.saveScenario()
                        }
                    }

                    button("", fontAwesome.create(CAMERA)) {
                        tooltip("Take snapshot")
                        setOnAction {
                            radarScreen.snapshot()
                        }
                    }

                    button("", fontAwesome.create(COGS)) {
                        disableProperty().bind(
                            designerController.calculatingHitsProperty
                                .or(simulationController.simulationRunningProperty)
                        )
                        tooltip("Compute scenario")

                        setOnMouseClicked {
                            if (it.button != MouseButton.PRIMARY) {
                                return@setOnMouseClicked
                            }
                            designerController.computeScenario(debug = it.isShiftDown)
                        }
                    }

                    button("", fontAwesome.create(PLAY)) {
                        disableProperty().bind(
                            simulationController.simulationRunningProperty
                                .or(designerController.calculatingHitsProperty)
                        )

                        tooltip("Begin simulation")

                        setOnAction {
                            runAsync {
                                simulationController.startSimulation(
                                    radarScreen.simulatedCurrentTimeSecProperty.get(),
                                    { progress, message ->
                                        updateMessage(message)
                                        val max = simulationController.radarParameters.azimuthChangePulse * designerController.scenario.simulationDurationMin * MIN_TO_S / simulationController.radarParameters.seekTimeSec
                                        updateProgress(progress, max)
                                    }
                                )
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

                                    combobox<Int> {
                                        tooltip("Number of previous hits to display")
                                        items = listOf(0, 1, 2, 3, 4, 5, 6).observable()

                                        value = radarScreen.displayParameters.plotHistoryCount
                                        valueProperty().addListener { _, _, newValue ->
                                            radarScreen.configPlotHistory(newValue)
                                        }
                                    }
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
                                    tooltip("Select clutter map")
                                    setOnAction {
                                        val file = chooseFile("Select clutter map", arrayOf(FileChooser.ExtensionFilter("Image  files", "*.jpg", "*.png")), FileChooserMode.Single)
                                            .firstOrNull() ?: return@setOnAction

                                        designerController.scenario.clutter = Clutter(file)
                                    }
                                }

                                button("", fontAwesome.create(ERASER)) {
                                    tooltip("Reset clutter")

                                    setOnAction {
                                        designerController.scenario.clutter = Clutter()
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
                    padding = Insets.EMPTY
                    this += movingTargetEditor.root
                    autosize()
                })

            }


        }

    }
}
