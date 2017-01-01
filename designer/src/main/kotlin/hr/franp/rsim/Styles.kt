package hr.franp.rsim

import javafx.geometry.VPos
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import javafx.scene.text.TextAlignment
import tornadofx.Stylesheet
import tornadofx.cssclass

class Styles : Stylesheet() {
    companion object {

        val radarScreen by cssclass()
        val movingTargetPositionLabel by cssclass()
        val distanceMarkerCircle by cssclass()
        val distanceMarkerLabel by cssclass()
        val azimuthMarkerLabel by cssclass()
        val azimuthMarkerLine by cssclass()
        val movingTargetCourseLine by cssclass()
        val movingTargetPathMarker by cssclass()
        val movingTargetRectangle by cssclass()
        val imageMovingTargetRectangle by cssclass()
        val movingTargetTestTwoWedge by cssclass()
        val movingTargetTestOneCircle by cssclass()

        // Define our colors
        val radarBgColor = Color.WHITESMOKE
        val radarFgColor = Color.DARKGRAY
        val stationaryTargetColor = Color.DARKGRAY
        val movingTargetHitFill = Color.BLUE
        val stationaryTargetHitFill = Color.GREEN
    }

    init {
        radarScreen {
            backgroundColor += radarBgColor
        }

        s(distanceMarkerCircle) {
            stroke = radarFgColor
        }

        s(distanceMarkerLabel) {
            fill = radarFgColor
            textAlignment = TextAlignment.CENTER
        }

        s(azimuthMarkerLabel) {
            fill = radarFgColor
            textAlignment = TextAlignment.CENTER
            textOrigin = VPos.CENTER
        }

        s(azimuthMarkerLine) {
            stroke = radarFgColor
            fill = Color.TRANSPARENT
        }

        s(movingTargetCourseLine) {
            stroke = Color.RED
            fill = Color.TRANSPARENT
        }

        s(movingTargetPathMarker) {
            stroke = Color.RED
            fill = Color.WHITE
        }

        s(movingTargetPositionLabel) {
            fill = radarFgColor
            fontWeight = FontWeight.EXTRA_BOLD
            textAlignment = TextAlignment.CENTER
        }

        s(movingTargetRectangle) {
            stroke = Color.RED
            fill = Styles.radarBgColor
        }

        s(imageMovingTargetRectangle) {
            fill = Color.TRANSPARENT
        }

        s(movingTargetTestOneCircle) {
            fill = Color.TRANSPARENT
            stroke = Color.RED
        }

        s(movingTargetTestTwoWedge) {
            fill = Color.RED
            stroke = Color.RED
        }


    }
}
