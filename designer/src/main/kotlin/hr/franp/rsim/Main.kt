package hr.franp.rsim

import javafx.stage.Stage
import tornadofx.App
import tornadofx.importStylesheet
import tornadofx.reloadStylesheetsOnFocus

class Main : App(DesignerView::class, Styles::class) {

    init {
        reloadStylesheetsOnFocus()
    }

    override fun start(stage: Stage) {
        super.start(stage.apply {
            minWidth = 1240.0
            minHeight = 768.0
            isFullScreen = true
        })
    }
}
