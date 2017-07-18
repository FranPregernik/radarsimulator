package hr.franp.rsim

import javafx.stage.Stage
import org.controlsfx.glyphfont.FontAwesome
import org.controlsfx.glyphfont.GlyphFont
import org.controlsfx.glyphfont.GlyphFontRegistry
import org.slf4j.bridge.SLF4JBridgeHandler
import tornadofx.*

class Main : App(DesignerView::class, Styles::class) {

    val radarScreenView = find(RadarScreenView::class)
    val simController = find(SimulatorController::class)

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

    override fun stop() {
        radarScreenView.onUndock()
        simController.close()
        super.stop()
    }
}
