package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.beans.binding.*
import javafx.beans.property.*
import javafx.beans.value.*
import javafx.geometry.*
import javafx.scene.control.*
import javafx.scene.layout.*
import org.controlsfx.glyphfont.*
import org.controlsfx.glyphfont.FontAwesome.Glyph.*
import tornadofx.*


class MovingTargetEditorView : View() {
    private val movingTargetSelector: MovingTargetSelectorView by inject()

    override val root = VBox()

    private val designerController: DesignerController by inject()
    private val simulatorController: SimulatorController by inject()
    private val radarScreen: RadarScreenView by inject()
    private var fontAwesome = GlyphFontRegistry.font("FontAwesome")
    private val dcsc = DistanceStringConverter()
    private val acsc = AngleStringConverter()
    private val scsc = SpeedStringConverter()
    private var coordinateClick by property<ChangeListener<RadarCoordinate>>()

    private var form: Form? = null

    init {

        with(root) {
            padding = Insets.EMPTY

            maxWidth = 400.0
            minWidth = 400.0

            this += movingTargetSelector.root
        }

        designerController.selectedMovingTargetProperty.addListener { _, _, _ ->
            // reinit form
            if (form != null) {
                form?.removeFromParent()
            }
            form = initEditForm()
        }
    }

    private fun initEditForm(): Form {
        var directionsTableView: TableView<Direction>? = null
        var removeDirectionItemButton: Button? = null
        var setDirectionEndpointButton: Button? = null
        var targetTypeSelector: ComboBox<MovingTargetType>? = null
        var initialDistanceField: TextField? = null
        var initialAzimuthField: TextField? = null
        var startingTimeField: TextField? = null
        var jammingSourceSelector: CheckBox? = null
        var synchroPulseRadarJammingSelector: CheckBox? = null
        var synchroPulseDelaySelector: Slider? = null

        val form = form {

            fieldset {

                field("Type") {
                    tooltip("Change the moving target type")

                    targetTypeSelector = combobox<MovingTargetType> {

                        disableProperty().bind(
                            designerController.calculatingHitsProperty
                                .or(simulatorController.simulationRunningProperty)
                                .or(designerController.selectedMovingTargetProperty.isNull)
                        )

                        items = listOf(
                            MovingTargetType.Point,
                            MovingTargetType.Test1,
                            MovingTargetType.Test2,
                            MovingTargetType.Cloud1,
                            MovingTargetType.Cloud2
                        ).observable()

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

                            disableProperty().bind(
                                designerController.calculatingHitsProperty
                                    .or(simulatorController.simulationRunningProperty)
                                    .or(designerController.selectedMovingTargetProperty.isNull)
                            )

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

                            disableProperty().bind(
                                designerController.calculatingHitsProperty
                                    .or(simulatorController.simulationRunningProperty)
                                    .or(designerController.selectedMovingTargetProperty.isNull)
                            )

                        }


                        button("", fontAwesome.create(CROSSHAIRS)) {
                            tooltip("Set coordinate by mouse click in the radar screen")

                            gridpaneConstraints {
                                columnRowIndex(2, 0)
                                rowSpan = 2
                            }

                            disableProperty().bind(
                                designerController.calculatingHitsProperty
                                    .or(simulatorController.simulationRunningProperty)
                                    .or(designerController.selectedMovingTargetProperty.isNull)
                            )

                            setOnAction {
                                if (coordinateClick != null) {
                                    radarScreen.mouseClickProperty.removeListener(coordinateClick)
                                }
                                coordinateClick = ChangeListener { _, _, newValue ->
                                    radarScreen.mouseClickProperty.removeListener(coordinateClick)

                                    // modify model
                                    designerController.selectedMovingTarget.initialPosition.rKm = newValue.rKm
                                    designerController.selectedMovingTarget.initialPosition.azDeg = newValue.azDeg

                                }
                                radarScreen.mouseClickProperty.addListener(coordinateClick)
                            }
                        }

                        label("t [sec]:") {
                            tooltip("Starting time")

                            gridpaneConstraints {
                                columnRowIndex(0, 2)
                            }
                        }
                        startingTimeField = textfield {
                            gridpaneConstraints {
                                columnRowIndex(1, 2)
                            }

                            disableProperty().bind(
                                designerController.calculatingHitsProperty
                                    .or(simulatorController.simulationRunningProperty)
                                    .or(designerController.selectedMovingTargetProperty.isNull)
                            )

                        }
                    }

                }

                field("JS") {
                    tooltip("Jamming source")

                    jammingSourceSelector = checkbox {

                        disableProperty().bind(
                            designerController.calculatingHitsProperty
                                .or(simulatorController.simulationRunningProperty)
                                .or(designerController.selectedMovingTargetProperty.isNull)
                        )

                        setOnAction {
                            designerController.selectedMovingTarget.jammingSource = selectedProperty().get()
                        }
                    }
                }.apply {
                    disableProperty().bind(
                        targetTypeSelector?.valueProperty()?.isNotEqualTo(MovingTargetType.Point)
                    )
                }

                field("SPRJ") {
                    tooltip("Synchronous pulse radar jamming")

                    hbox(4.0) {
                        synchroPulseRadarJammingSelector = checkbox {

                            disableProperty().bind(
                                designerController.calculatingHitsProperty
                                    .or(simulatorController.simulationRunningProperty)
                                    .or(designerController.selectedMovingTargetProperty.isNull)
                            )

                            setOnAction {
                                designerController.selectedMovingTarget.synchroPulseRadarJamming = selectedProperty().get()
                            }
                        }

                        synchroPulseDelaySelector = slider {
                            visibleProperty().bind(synchroPulseRadarJammingSelector?.selectedProperty())

                            disableProperty().bind(
                                designerController.calculatingHitsProperty
                                    .or(simulatorController.simulationRunningProperty)
                                    .or(designerController.selectedMovingTargetProperty.isNull)
                            )

                            tooltip("Controls sync pulse delay")

                            prefWidth = 200.0

                            min = -15000.0
                            max = 15000.0
                            blockIncrement = 150.0 // TODO: radarParameters distanceResolutionKm

                        }

                        label {
                            visibleProperty().bind(synchroPulseRadarJammingSelector?.selectedProperty())
                            textProperty().bind(
                                synchroPulseDelaySelector?.valueProperty()?.asString("%.1f [m]")
                            )
                        }

                    }
                }.apply {
                    disableProperty().bind(
                        targetTypeSelector?.valueProperty()?.isNotEqualTo(MovingTargetType.Point)
                    )
                }
            }

            fieldset {
                visibleProperty().bind(
                    Bindings.or(
                        targetTypeSelector?.valueProperty()?.isEqualTo(MovingTargetType.Point),
                        targetTypeSelector?.valueProperty()?.isEqualTo(MovingTargetType.Test1)
                    ).or(
                        targetTypeSelector?.valueProperty()?.isEqualTo(MovingTargetType.Test2)
                    )
                )
                labelPosition = Orientation.VERTICAL

                field("Courses") {

                    vbox {
                        padding = Insets.EMPTY

                        toolbar {
                            button("", fontAwesome.create(PLUS)) {
                                tooltip("Add a new course to the list")

                                disableProperty().bind(
                                    designerController.calculatingHitsProperty
                                        .or(simulatorController.simulationRunningProperty)
                                        .or(designerController.selectedMovingTargetProperty.isNull)
                                )

                                setOnAction {
                                    designerController.selectedMovingTarget.directionsProperty().value.add(Direction(
                                        destination = RadarCoordinate(0.0, 0.0),
                                        speedKmh = 0.0
                                    ))
                                }
                            }
                            removeDirectionItemButton = button("", fontAwesome.create(TRASH)) {
                                tooltip("Remove the selected course to the list")

                                disableProperty().bind(
                                    designerController.calculatingHitsProperty
                                        .or(simulatorController.simulationRunningProperty)
                                        .or(designerController.selectedMovingTargetProperty.isNull)
                                )

                                setOnAction {
                                    val direction = directionsTableView?.selectionModel?.selectedItem ?: return@setOnAction
                                    designerController.selectedMovingTarget.directionsProperty().value.remove(direction)
                                }
                            }

                            setDirectionEndpointButton = button("", fontAwesome.create(CROSSHAIRS)) {
                                tooltip("Set the course destination coordinates with mouse in the radar screen")

                                disableProperty().bind(
                                    designerController.calculatingHitsProperty
                                        .or(simulatorController.simulationRunningProperty)
                                        .or(designerController.selectedMovingTargetProperty.isNull)
                                )

                                setOnAction {
                                    if (coordinateClick != null) {
                                        radarScreen.mouseClickProperty.removeListener(coordinateClick)
                                    }
                                    coordinateClick = ChangeListener { _, _, newValue ->
                                        radarScreen.mouseClickProperty.removeListener(coordinateClick)

                                        val segment = directionsTableView?.selectedItem ?: return@ChangeListener

                                        // modify model
                                        segment.destination.rKm = newValue.rKm
                                        segment.destination.azDeg = newValue.azDeg

                                    }
                                    radarScreen.mouseClickProperty.addListener(coordinateClick)
                                }
                            }
                        }

                        directionsTableView = tableview<Direction> {
                            disableProperty().bind(designerController.selectedMovingTargetProperty.isNull)

                            maxHeight = 200.0

                            removeDirectionItemButton?.disableProperty()?.bind(
                                designerController.selectedMovingTargetProperty.isNull.or(
                                    selectionModel.selectedItemProperty().isNull
                                )
                            )

                            setDirectionEndpointButton?.disableProperty()?.bind(
                                designerController.selectedMovingTargetProperty.isNull.or(
                                    selectionModel.selectedItemProperty().isNull
                                )
                            )

                            editableProperty().bind(
                                designerController.selectedMovingTargetProperty.isNotNull
                                    .and(designerController.calculatingHitsProperty.not())
                                    .and(simulatorController.simulationRunningProperty.not())
                            )

                            columnResizePolicy = SmartResize.POLICY

                            column("r [km]", Direction::rProperty).apply {
                                useTextField(dcsc)
                                pctWidth(25.0)
                                isSortable = false

                                setOnEditCommit {
                                    it.rowValue.rProperty().value = it.newValue
                                }
                            }

                            column("az [deg]", Direction::azProperty).apply {

                                useTextField(acsc)
                                pctWidth(25.0)
                                isSortable = false

                                setOnEditCommit {
                                    it.rowValue.azProperty().value = it.newValue
                                }
                            }

                            column("spd [km/h]", Direction::speedKmhProperty).apply {

                                useTextField(scsc)
                                pctWidth(25.0)

                                isSortable = false

                                setOnEditCommit {
                                    it.rowValue.speedKmhProperty().value = it.newValue
                                }
                            }

                            // TODO: add virtual column
                            //                                column("hdg [deg]", Direction::speedKmhProperty).apply {
                            //                                    isEditable = false
                            //                                    isSortable = false
                            //
                            //                                    pctWidth(25.0)
                            //                                }

                        }
                    }

                }
            }
        }

        designerController.selectedMovingTarget?.apply {
            targetTypeSelector?.valueProperty()?.bindBidirectional(typeProperty())
            jammingSourceSelector?.selectedProperty()?.bindBidirectional(jammingSourceProperty())
            synchroPulseRadarJammingSelector?.selectedProperty()?.bindBidirectional(synchroPulseRadarJammingProperty())
            synchroPulseDelaySelector?.valueProperty()?.bindBidirectional(
                synchroPulseDelayMProperty() as Property<Number>
            )
            initialDistanceField?.textProperty()?.bindBidirectional(initialPosition.rProperty(), dcsc)
            initialAzimuthField?.textProperty()?.bindBidirectional(initialPosition.azDegProperty(), acsc)
            startingTimeField?.textProperty()?.bindBidirectional(startingTimeProperty(), dcsc)
            directionsTableView?.itemsProperty()?.bindBidirectional(directionsProperty())

            directionsTableView?.itemsProperty()?.addListener { _, _, _ ->
                // hook up all
            }
        }

        return form
    }

}
