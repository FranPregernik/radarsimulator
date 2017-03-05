package hr.franp.rsim

import javafx.stage.*
import org.controlsfx.glyphfont.*
import org.slf4j.bridge.*
import tornadofx.*

class Main : App(DesignerView::class, Styles::class) {

    init {
        // Optionally remove existing handlers attached to j.u.l root logger
        SLF4JBridgeHandler.removeHandlersForRootLogger()  // (since SLF4J 1.6.5)

        // add SLF4JBridgeHandler to j.u.l's root logger, should be done once during
        // the initialization phase of your application
        SLF4JBridgeHandler.install()

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
