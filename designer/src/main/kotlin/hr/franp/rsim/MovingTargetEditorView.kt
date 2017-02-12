package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.beans.value.*
import javafx.geometry.*
import javafx.scene.control.*
import javafx.scene.layout.*
import org.controlsfx.glyphfont.*
import org.controlsfx.glyphfont.FontAwesome.Glyph.*
import tornadofx.*


class MovingTargetEditorView : View() {

    override val root = VBox().apply {
        padding = Insets.EMPTY
    }

    private val controller: DesignerController by inject()
    private val radarScreen: RadarScreenView by inject()
    private var fontAwesome = GlyphFontRegistry.font("FontAwesome")
    private val dcsc = DistanceStringConverter()
    private val acsc = AngleStringConverter()
    private val scsc = SpeedStringConverter()
    private var coordinateClick by property<ChangeListener<RadarCoordinate>>()

    var directionsTableView: TableView<Direction> by singleAssign()
    var removeDirectionItemButton: Button by singleAssign()
    var setDirectionEndpointButton: Button by singleAssign()
    var targetTypeSelector: ComboBox<MovingTargetType> by singleAssign()
    var initialDistanceField: TextField by singleAssign()
    var initialAzimuthField: TextField by singleAssign()

    init {

        with(root) {

            form {

                fieldset {
                    labelPosition = Orientation.VERTICAL

                    field("Type") {
                        tooltip("Change the moving target type")

                        targetTypeSelector = combobox<MovingTargetType> {
                            disableProperty().bind(controller.selectedMovingTargetProperty.isNull)
                            items = MovingTargetType.values().toList().observable()

                            setOnAction {
                                radarScreen.drawMovingTargets()
                            }
                        }
                    }

                    field("Initial") {
                        gridpane {

                            label("r [km]:") {
                                tooltip("Distance from radar in km")

                                gridpaneConstraints {
                                    columnRowIndex(0, 0)
                                }
                            }
                            initialDistanceField = textfield {
                                gridpaneConstraints {
                                    columnRowIndex(1, 0)
                                }

                                disableProperty().bind(controller.selectedMovingTargetProperty.isNull)

                                textProperty().addListener { observableValue, oldValue, newValue ->
                                    radarScreen.drawMovingTargets()
                                }
                            }

                            label("az [deg]:") {
                                tooltip("Azimuth of target in degrees")

                                gridpaneConstraints {
                                    columnRowIndex(0, 1)
                                }
                            }
                            initialAzimuthField = textfield {
                                gridpaneConstraints {
                                    columnRowIndex(1, 1)
                                }

                                disableProperty().bind(controller.selectedMovingTargetProperty.isNull)

                                textProperty().addListener { observableValue, oldValue, newValue ->
                                    radarScreen.drawMovingTargets()
                                }
                            }


                            button("", fontAwesome.create(CROSSHAIRS)) {
                                tooltip("Set coordinate by mouse click in the radar screen")

                                gridpaneConstraints {
                                    columnRowIndex(2, 0)
                                    rowSpan = 2
                                }

                                disableProperty().bind(controller.selectedMovingTargetProperty.isNull)

                                setOnAction {
                                    if (coordinateClick != null) {
                                        radarScreen.mouseClickProperty.removeListener(coordinateClick)
                                    }
                                    coordinateClick = ChangeListener { observableValue, oldValue, newValue ->
                                        radarScreen.mouseClickProperty.removeListener(coordinateClick)

                                        // modify model
                                        controller.selectedMovingTarget.initialPosition.rKm = newValue.rKm
                                        controller.selectedMovingTarget.initialPosition.azDeg = newValue.azDeg

                                        // redraw
                                        radarScreen.drawMovingTargets()
                                    }
                                    radarScreen.mouseClickProperty.addListener(coordinateClick)
                                }
                            }
                        }


                    }

                    field("Courses") {

                        vbox {
                            padding = javafx.geometry.Insets.EMPTY

                            toolbar {
                                button("", fontAwesome.create(PLUS)) {
                                    tooltip("Add a new course to the list")

                                    disableProperty().bind(controller.selectedMovingTargetProperty.isNull)

                                    setOnAction {
                                        controller.selectedMovingTarget.directionsProperty().value.add(hr.franp.rsim.models.Direction())
                                        radarScreen.drawMovingTargets()
                                    }
                                }
                                removeDirectionItemButton = button("", fontAwesome.create(MINUS)) {
                                    tooltip("Remove the selected course to the list")

                                    setOnAction {
                                        val direction = directionsTableView.selectionModel.selectedItem ?: return@setOnAction
                                        controller.selectedMovingTarget.directionsProperty().value.remove(direction)
                                        radarScreen.drawMovingTargets()
                                    }
                                }
                                setDirectionEndpointButton = button("", fontAwesome.create(CROSSHAIRS)) {
                                    tooltip("Set the course destination coordinates with mouse in the radar screen")

                                    disableProperty().bind(controller.selectedMovingTargetProperty.isNull)

                                    setOnAction {
                                        if (coordinateClick != null) {
                                            radarScreen.mouseClickProperty.removeListener(coordinateClick)
                                        }
                                        coordinateClick = ChangeListener { observableValue, oldValue, newValue ->
                                            radarScreen.mouseClickProperty.removeListener(coordinateClick)

                                            val segment = directionsTableView.selectedItem ?: return@ChangeListener

                                            // modify model
                                            segment.destination.rKm = newValue.rKm
                                            segment.destination.azDeg = newValue.azDeg

                                            // redraw
                                            radarScreen.drawMovingTargets()
                                        }
                                        radarScreen.mouseClickProperty.addListener(coordinateClick)
                                    }
                                }
                            }

                            directionsTableView = tableview<Direction> {
                                disableProperty().bind(controller.selectedMovingTargetProperty.isNull)

                                removeDirectionItemButton.disableProperty().bind(
                                    controller.selectedMovingTargetProperty.isNull.or(
                                        selectionModel.selectedItemProperty().isNull
                                    )
                                )

                                setDirectionEndpointButton.disableProperty().bind(
                                    controller.selectedMovingTargetProperty.isNull.or(
                                        selectionModel.selectedItemProperty().isNull
                                    )
                                )

                                isEditable = true
                                columnResizePolicy = tornadofx.SmartResize.POLICY

                                column("r [km]", Direction::rProperty).apply {
                                    useTextField(dcsc) {
                                        radarScreen.drawMovingTargets()
                                    }
                                    pctWidth(25.0)
                                    isSortable = false

                                    setOnEditStart {
                                        radarScreen.drawMovingTargets()
                                    }
                                }

                                column("az [deg]", Direction::azProperty).apply {
                                    useTextField(acsc) {
                                        radarScreen.drawMovingTargets()
                                    }
                                    pctWidth(25.0)
                                    isSortable = false

                                    setOnEditStart {
                                        radarScreen.drawMovingTargets()
                                    }
                                }

                                column("speed [km/h]", Direction::speedKmhProperty).apply {
                                    useTextField(scsc) {
                                        radarScreen.drawMovingTargets()
                                    }
                                    pctWidth(25.0)
                                    isSortable = false

                                    setOnEditCommit {
                                        radarScreen.drawMovingTargets()
                                    }
                                }

                            }
                        }

                    }
                }
            }

        }

        controller.selectedMovingTargetProperty.addListener { observableValue, oldMovingTarget, newMovingTarget ->

            // unbind/rebind
            oldMovingTarget?.apply {
                typeProperty().unbindBidirectional(targetTypeSelector.valueProperty())
                initialDistanceField.textProperty().unbindBidirectional(initialPosition.rProperty())
                initialAzimuthField.textProperty().unbindBidirectional(initialPosition.azDegProperty())
                directionsProperty().unbindBidirectional(directionsTableView.itemsProperty())
            }

            if (newMovingTarget == null) return@addListener

            targetTypeSelector.valueProperty().bindBidirectional(newMovingTarget.typeProperty())
            initialDistanceField.textProperty().bindBidirectional(newMovingTarget.initialPosition.rProperty(), dcsc)
            initialAzimuthField.textProperty().bindBidirectional(newMovingTarget.initialPosition.azDegProperty(), acsc)
            directionsTableView.itemsProperty().bindBidirectional(newMovingTarget.directionsProperty())

            directionsTableView.itemsProperty().addListener { observableValue, oldDirections, newDirections ->
                // hook up all
            }
        }
    }

}
