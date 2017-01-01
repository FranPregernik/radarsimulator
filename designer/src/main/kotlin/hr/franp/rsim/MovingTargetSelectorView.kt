package hr.franp.rsim

import hr.franp.rsim.models.MovingTarget
import javafx.scene.control.ComboBox
import org.controlsfx.glyphfont.FontAwesome
import org.controlsfx.glyphfont.GlyphFontRegistry
import tornadofx.View
import tornadofx.borderpane
import tornadofx.button
import tornadofx.combobox
import tornadofx.getProperty
import tornadofx.label
import tornadofx.property
import tornadofx.rebind
import tornadofx.rebindOnChange
import tornadofx.singleAssign
import tornadofx.toolbar
import tornadofx.tooltip

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
