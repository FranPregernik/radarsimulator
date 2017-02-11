package hr.franp.rsim

import javafx.stage.*
import org.controlsfx.glyphfont.*
import tornadofx.*

class Main : App(DesignerView::class, Styles::class) {

    init {
        reloadStylesheetsOnFocus()

        val faStream = Main::class.java.getResourceAsStream("/fontawesome-webfont.ttf")
        val fa: GlyphFont = FontAwesome(faStream)
        GlyphFontRegistry.register(fa)
    }

    override fun start(stage: Stage) {
        super.start(stage.apply {
            minWidth = 1240.0
            minHeight = 768.0
            isFullScreen = true
        })
    }
}
