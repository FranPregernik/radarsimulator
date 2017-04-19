package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.scene.control.*
import org.controlsfx.glyphfont.*
import org.controlsfx.glyphfont.FontAwesome.Glyph.*
import tornadofx.*

class MovingTargetSelectorView : View() {
    private val designerController: DesignerController by inject()
    private val simulatorController: SimulatorController by inject()
    private var fontAwesome = GlyphFontRegistry.font("FontAwesome")
    private var targetSelector by singleAssign<ComboBox<MovingTarget>>()

    override val root = toolbar {
        label("Target") {
            tooltip("Select target to edit")

            setOnMouseClicked {
                targetSelector.selectionModel.clearSelection()
                designerController.selectedMovingTargetProperty.set(null)
            }
        }

        targetSelector = combobox<MovingTarget> {
            designerController.scenarioProperty.addListener { _, _, _ ->
                itemsProperty().bind(designerController.scenario.movingTargetsProperty())
            }
            itemsProperty().bind(designerController.scenario.movingTargetsProperty())

            // Update the target inside the view model on selection change
            valueProperty().bindBidirectional(designerController.selectedMovingTargetProperty)

        }

        button("", fontAwesome.create(FontAwesome.Glyph.PLUS)) {
            tooltip("Adds a new target")

            disableProperty().bind(
                designerController.calculatingHitsProperty.or(
                    simulatorController.simulationRunningProperty
                )
            )

            setOnAction {
                val newMovingTarget = MovingTarget().apply {
                    name = "T${designerController.scenario.movingTargets.size + 1}"
                }
                designerController.scenario.movingTargets.add(newMovingTarget)
                targetSelector.selectionModel.select(newMovingTarget)
            }
        }

        button("", fontAwesome.create(TRASH)) {
            tooltip("Removes the currently selected target")

            disableProperty().bind(
                designerController.calculatingHitsProperty
                    .or(simulatorController.simulationRunningProperty)
                    .or(designerController.selectedMovingTargetProperty.isNull)
            )

            setOnAction {
                designerController.scenario.movingTargets.remove(designerController.selectedMovingTarget)
                targetSelector.selectionModel.clearSelection()
                designerController.selectedMovingTargetProperty.set(null)
            }
        }

    }
}
