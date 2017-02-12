package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.scene.control.*
import org.controlsfx.glyphfont.*
import tornadofx.*

class MovingTargetSelectorView : View() {
    private val controller: DesignerController by inject()
    private var fontAwesome = GlyphFontRegistry.font("FontAwesome")
    private var targetSelector by singleAssign<ComboBox<MovingTarget>>()

    override val root = toolbar {
        label("Target") {
            tooltip("Select target to edit")

            setOnMouseClicked {
                targetSelector.selectionModel.clearSelection()
                controller.selectedMovingTargetProperty.set(null)
            }
        }

        targetSelector = combobox<MovingTarget> {
            itemsProperty().bind(controller.scenario.movingTargetsProperty())

            // Update the target inside the view model on selection change
            valueProperty().bindBidirectional(controller.selectedMovingTargetProperty)

        }

        button("", fontAwesome.create(FontAwesome.Glyph.PLUS)) {
            tooltip("Adds a new target")

            setOnAction {
                val newMovingTarget = MovingTarget().apply {
                    name = "T${controller.scenario.movingTargets.size + 1}"
                }
                controller.scenario.movingTargets.add(newMovingTarget)
                targetSelector.selectionModel.select(newMovingTarget)
            }
        }

        button("", fontAwesome.create(FontAwesome.Glyph.MINUS)) {
            tooltip("Removes the currently selected target")

            disableProperty().bind(controller.selectedMovingTargetProperty.isNull)

            setOnAction {
                controller.scenario.movingTargets.remove(controller.selectedMovingTarget)
                targetSelector.selectionModel.clearSelection()
                controller.selectedMovingTargetProperty.set(null)
            }
        }

    }
}
