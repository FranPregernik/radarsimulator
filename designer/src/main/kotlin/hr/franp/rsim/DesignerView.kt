package hr.franp.rsim

import hr.franp.rsim.models.*
import hr.franp.rsim.models.AzimuthMarkerType.*
import hr.franp.rsim.models.DistanceUnit.*
import javafx.geometry.*
import javafx.scene.control.*
import javafx.scene.image.*
import javafx.scene.layout.*
import javafx.scene.text.*
import javafx.stage.*
import javafx.util.converter.*
import org.controlsfx.glyphfont.*
import org.controlsfx.glyphfont.FontAwesome.Glyph.*
import tornadofx.*
import java.io.*
import java.util.*
import java.util.zip.*

class DesignerView : View() {
    override val root = BorderPane()

    private var fontAwesome = GlyphFontRegistry.font("FontAwesome")
    private val controller: DesignerController by inject()
    private val radarScreen: RadarScreenView by inject()
    private val movingTargetEditor: MovingTargetEditorView by inject()
    private val movingTargetSelector: MovingTargetSelectorView by inject()

    init {

        with(root) {

            maxWidth = 500.0
            minWidth = 300.0

            // add and ensure radar screen fills the space
            center = radarScreen.root.apply {

            }

            right = vbox {

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
                                            controller.displayParameters.distanceUnit = Km
                                            radarScreen.drawStaticMarkers()
                                        }
                                    }
                                    togglebutton(NM.toString()) {
                                        setOnAction {
                                            controller.displayParameters.distanceUnit = NM
                                            radarScreen.drawStaticMarkers()
                                        }
                                    }
                                }

                                togglegroup {
                                    togglebutton("0").setOnAction {
                                        controller.displayParameters.distanceStepKm = 0.0
                                        radarScreen.drawStaticMarkers()
                                    }
                                    togglebutton("10").setOnAction {
                                        controller.displayParameters.distanceStepKm = 10.0
                                        radarScreen.drawStaticMarkers()
                                    }
                                    togglebutton("50") {
                                        isSelected = true
                                        setOnAction {
                                            controller.displayParameters.distanceStepKm = 50.0
                                            radarScreen.drawStaticMarkers()
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
                                            controller.displayParameters.azimuthMarkerType = FULL
                                            radarScreen.drawStaticMarkers()
                                        }
                                    }
                                    togglebutton(MIN.toString()) {
                                        setOnAction {
                                            controller.displayParameters.azimuthMarkerType = MIN
                                            radarScreen.drawStaticMarkers()
                                        }
                                    }
                                }

                                togglegroup {
                                    togglebutton("0").setOnAction {
                                        controller.displayParameters.azimuthSteps = 0
                                        radarScreen.drawStaticMarkers()
                                    }
                                    togglebutton("5").setOnAction {
                                        controller.displayParameters.azimuthSteps = 72
                                        radarScreen.drawStaticMarkers()
                                    }
                                    togglebutton("10") {
                                        isSelected = true
                                        setOnAction {
                                            controller.displayParameters.azimuthSteps = 36
                                            radarScreen.drawStaticMarkers()
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

                            field("Clutter hit layer") {

                                slider {
                                    tooltip("Controls transparency of the clutter hit layer")

                                    min = 0.0
                                    max = 1.0
                                    blockIncrement = 0.1
                                    bind(radarScreen.stationaryHitsLayerOpacityProperty)
                                }
                            }

                            field("Clutter map") {
                                button("", fontAwesome.create(FILE_PHOTO_ALT)) {
                                    setOnAction {
                                        val files = chooseFile("Select clutter map", arrayOf(FileChooser.ExtensionFilter("Image  files", "*.jpg")))
                                        println("The user chose $files")
                                        if (files.isNotEmpty()) {
                                            controller.scenario.stationaryTargets = StationaryTarget(files.first())
                                            radarScreen.drawStationaryTargets()
                                            this.tooltip = Tooltip("Select clutter map").apply {
                                                graphic = ImageView(controller.scenario.stationaryTargets.getImage(100, 100))
                                            }
                                        }
                                    }
                                }

                            }
                        }
                        fieldset {
                            field("Tsim") {

                                button("", fontAwesome.create(STEP_BACKWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(0.0))
                                    setOnAction {
                                        controller.displayParameters.simulatedCurrentTimeSec = 0.0
                                    }
                                }
                                button("", fontAwesome.create(FAST_BACKWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(0.0))
                                    minWidth = Font.getDefault().size * 3
                                    setOnAction {
                                        val simulatedCurrentTimeSec = controller.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec - 10 * controller.radarParameters.seekTimeSec
                                        controller.displayParameters.simulatedCurrentTimeSec = Math.max(newTime, 0.0)
                                    }
                                }
                                button("", fontAwesome.create(BACKWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(0.0))
                                    setOnAction {
                                        val simulatedCurrentTimeSec = controller.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec - controller.radarParameters.seekTimeSec
                                        controller.displayParameters.simulatedCurrentTimeSec = Math.max(newTime, 0.0)
                                    }
                                }

                                textfield {
                                    textProperty().bindBidirectional(controller.displayParameters.simulatedCurrentTimeSecProperty(), DoubleStringConverter())
                                    minWidth = Font.getDefault().size * 5
                                }

                                button("", fontAwesome.create(FORWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(controller.scenario.simulationDurationMin * MIN_TO_S))
                                    setOnAction {
                                        val simulatedCurrentTimeSec = controller.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec + controller.radarParameters.seekTimeSec
                                        controller.displayParameters.simulatedCurrentTimeSec = Math.min(newTime, controller.scenario.simulationDurationMin * MIN_TO_S)
                                    }
                                }
                                button("", fontAwesome.create(FAST_FORWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(controller.scenario.simulationDurationMin * MIN_TO_S))
                                    minWidth = Font.getDefault().size * 3
                                    setOnAction {
                                        val simulatedCurrentTimeSec = controller.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec + 10 * controller.radarParameters.seekTimeSec
                                        controller.displayParameters.simulatedCurrentTimeSec = Math.min(newTime, controller.scenario.simulationDurationMin * MIN_TO_S)
                                    }
                                }
                                button("", fontAwesome.create(STEP_FORWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(controller.scenario.simulationDurationMin * MIN_TO_S))
                                    setOnAction {
                                        controller.displayParameters.simulatedCurrentTimeSec = controller.scenario.simulationDurationMin * MIN_TO_S
                                    }
                                }

                            }
                            field {
                                button("Calculate") {

                                    tooltip("Calculate and display all hits")

                                    setOnAction {
                                        this.disableProperty().set(true)

                                        runAsync {

                                            FileOutputStream("clutter.bin.gz").use { clutterFile ->
                                                GZIPOutputStream(clutterFile).use { stream ->
                                                    stream.write(controller.radarParameters.seekTimeSec.toInt())
                                                    stream.write(controller.radarParameters.azimuthChangePulse.toInt())
                                                    stream.write(controller.radarParameters.impulsePeriodUs.toInt())

                                                    val clutterHits = controller.calculateClutterHits()
                                                    radarScreen.clutterHitsProperty.set(clutterHits)
                                                    stream.write(clutterHits.toByteArray())
                                                }
                                            }

                                            FileOutputStream("targets.bin.gz").use { targetFile ->
                                                GZIPOutputStream(targetFile).use { stream ->
                                                    stream.write(controller.radarParameters.seekTimeSec.toInt())
                                                    stream.write(controller.radarParameters.azimuthChangePulse.toInt())
                                                    stream.write(controller.radarParameters.impulsePeriodUs.toInt())

                                                    var targetHits = BitSet()

                                                    controller.calculateTargetHits().forEach {
                                                        targetHits.or(it)
                                                        stream.write(it.toByteArray())
                                                    }

                                                    radarScreen.targetHitsProperty.set(targetHits)
                                                }
                                            }
                                        } ui {
                                            this.disableProperty().set(false)
                                            radarScreen.drawHits()
                                        }

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
