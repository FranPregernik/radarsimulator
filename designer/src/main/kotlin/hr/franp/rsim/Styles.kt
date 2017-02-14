package hr.franp.rsim

import javafx.geometry.VPos
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import javafx.scene.text.TextAlignment
import tornadofx.*

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
        val movingTargetPlotMarker by cssclass()
        val imageMovingTargetRectangle by cssclass()
        val movingTargetTestTwoWedge by cssclass()
        val movingTargetTestOneCircle by cssclass()

        // Define our colors
        val radarBgColor = c("202020")
        val radarFgColor = Color.GRAY
        val movingTargetHitFill = Color.BLUE
        val stationaryTargetHitFill = Color.GREEN

        val movingTargetPositionLabelColor = Color.WHITE
        val movingTargetCourseLineColor = Color.RED
        val movingTargetPathMarkerFillColor = Color.DARKRED

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
            stroke = movingTargetCourseLineColor
            fill = Color.TRANSPARENT
        }

        s(movingTargetPathMarker) {
            stroke = movingTargetCourseLineColor
            fill = movingTargetPathMarkerFillColor
        }

        s(movingTargetPositionLabel) {
            fill = movingTargetPositionLabelColor
            fontWeight = FontWeight.EXTRA_BOLD
            textAlignment = TextAlignment.CENTER
        }

        s(movingTargetRectangle) {
            stroke = movingTargetCourseLineColor
            fill = Styles.radarBgColor
        }

        s(imageMovingTargetRectangle) {
            fill = Color.TRANSPARENT
        }

        s(movingTargetTestOneCircle) {
            fill = Color.TRANSPARENT
            stroke = movingTargetCourseLineColor
        }

        s(movingTargetTestTwoWedge) {
            fill = movingTargetCourseLineColor
            stroke = movingTargetCourseLineColor
        }

        s(movingTargetPlotMarker) {
            stroke = Color.GREEN
            fill = Color.GREEN
        }

    }
}
