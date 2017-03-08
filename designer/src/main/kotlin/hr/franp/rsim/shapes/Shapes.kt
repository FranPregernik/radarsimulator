package hr.franp.rsim.shapes

import hr.franp.rsim.*
import javafx.geometry.*
import javafx.scene.*
import javafx.scene.canvas.*
import javafx.scene.image.*
import javafx.scene.paint.*
import javafx.scene.shape.*
import javafx.scene.text.*
import javafx.scene.transform.*
import tornadofx.*
import java.lang.Math.*

class DistanceMarkerCircle(cp: Point2D, r: Double) : Circle(cp.x, cp.y, r, Color.TRANSPARENT) {
    init {
        addClass(Styles.distanceMarkerCircle)
        strokeWidth = 1.0
    }
}

class DistanceMarkerLabel(p: Point2D, text: String) : Text(p.x, p.y, text) {
    init {
        addClass(Styles.distanceMarkerLabel)
        font = Font(Font.getDefault().size)

        transforms.clear()
        transforms.add(Translate(-boundsInLocal.width / 2, boundsInLocal.height))
    }
}

class AzimuthMarkerLabel(p: Point2D, angleRad: Double) : Text(
    p.x,
    p.y,
    ((720 + toDegrees(angleToAzimuth(angleRad))) % 360).toInt().toString()
) {
    init {
        addClass(Styles.azimuthMarkerLabel)
        font = Font(Font.getDefault().size)

        transforms.clear()
        transforms.add(Translate(-boundsInLocal.width / 2, 0.0))
    }
}

class AzimuthMarkerLine(p1: Point2D, p2: Point2D) : Line(p1.x, p1.y, p2.x, p2.y) {
    init {
        addClass(Styles.azimuthMarkerLine)
        strokeWidth = 1.0
    }
}

class MovingTargetCourseLine(p1: Point2D, p2: Point2D) : Line(p1.x, p1.y, p2.x, p2.y) {
    init {
        addClass(Styles.movingTargetCourseLine)
        strokeWidth = 1.0
    }
}

class MovingTargetPathMarker(p: Point2D) : Circle(p.x, p.y, 1.0, Color.TRANSPARENT) {
    init {
        addClass(Styles.movingTargetPathMarker)
        strokeWidth = 1.0
    }
}

class MovingTargetPositionMarker(p: Point2D, text: String, width: Double = 10.0, height: Double = 10.0, color: Color, image: Image? = null) : Group() {
    val label = Text(
        p.x,
        p.y + height / 2 + 1.5 * Font.getDefault().size,
        text
    ).apply {
        addClass(Styles.movingTargetPositionLabel)
        font = Font(Font.getDefault().size)

        transforms.clear()
        transforms.add(Translate(-boundsInLocal.width / 2, 0.0))
    }

    val marker = Rectangle(
        p.x - width / 2.0,
        p.y - height / 2.0,
        width,
        height
    ).apply {
        addClass(Styles.movingTargetRectangle)
        if (image == null) {
            addClass(Styles.imageMovingTargetRectangle)
        }
        stroke = color
    }

    init {
        add(marker)
        add(label)
        if (image != null) {

            add(ImageView(image).apply {
                this.x = p.x - width / 2.0
                this.y = p.y - height / 2.0
                this.fitWidth = width
                this.fitHeight = height
            })
        }
    }
}

class MovingTargetPlotMarker(x: Double, y: Double, r: Double = 2.0) : Circle(x, y, r) {
    init {
        addClass(Styles.movingTargetPlotMarker)
    }
}

class Test1TargetHitMarker(x: Double, y: Double) : Group() {
    private val d = sqrt(pow(x, 2.0) + pow(y, 2.0))

    val marker = Circle(0.0, 0.0, d, Color.TRANSPARENT).apply {
        addClass(Styles.movingTargetTestOnePlotCircle)
        strokeWidth = 1.0
    }

    init {
        addClass(Styles.movingTargetPlotMarker)
        add(marker)
    }
}

class Test2TargetHitMarker(x: Double, y: Double, angleResolutionDeg: Double, maxDistance: Double) : Group() {
    private val startAngleDeg = 360 - floor(toDegrees(atan2(y, x)) / angleResolutionDeg) * angleResolutionDeg

    val marker = Arc(0.0, 0.0, maxDistance, maxDistance, startAngleDeg, angleResolutionDeg).apply {
        addClass(Styles.movingTargetTestTwoPlotWedge)
        strokeWidth = 1.0
        type = ArcType.ROUND
    }

    init {
        addClass(Styles.movingTargetPlotMarker)
        add(marker)
    }
}


fun GraphicsContext.movingHitMarker(innerRadius: Double, width: Double, angleRad: Double, spreadRad: Double) {
    val angleAlpha = angleRad - spreadRad / 2.0
    val angleAlphaNext = angleRad + spreadRad / 2.0
    val outerRadius = innerRadius + width

    //Point 1
    val pointX1 = innerRadius * cos(angleAlpha)
    val pointY1 = innerRadius * sin(angleAlpha)

    //Point 2
    val pointX2 = outerRadius * cos(angleAlpha)
    val pointY2 = outerRadius * sin(angleAlpha)

    //Point 3
    val pointX3 = outerRadius * cos(angleAlphaNext)
    val pointY3 = outerRadius * sin(angleAlphaNext)

    //Point 4
    val pointX4 = innerRadius * cos(angleAlphaNext)
    val pointY4 = innerRadius * sin(angleAlphaNext)

    fill = Styles.movingTargetHitFill
    beginPath()
    moveTo(pointX1, pointY1)
    lineTo(pointX2, pointY2)
    lineTo(pointX3, pointY3)
    lineTo(pointX4, pointY4)
    lineTo(pointX1, pointY1)
    fill()
    closePath()

}

fun GraphicsContext.stationaryHitMarker(innerRadius: Double, width: Double, angleRad: Double, spreadRad: Double) {
    val angleAlpha = angleRad - spreadRad / 2.0
    val angleAlphaNext = angleRad + spreadRad / 2.0
    val outerRadius = innerRadius + width

    //Point 1
    val pointX1 = innerRadius * cos(angleAlpha)
    val pointY1 = innerRadius * sin(angleAlpha)

    //Point 2
    val pointX2 = outerRadius * cos(angleAlpha)
    val pointY2 = outerRadius * sin(angleAlpha)

    //Point 3
    val pointX3 = outerRadius * cos(angleAlphaNext)
    val pointY3 = outerRadius * sin(angleAlphaNext)

    //Point 4
    val pointX4 = innerRadius * cos(angleAlphaNext)
    val pointY4 = innerRadius * sin(angleAlphaNext)

    fill = Styles.stationaryTargetHitFill
    beginPath()
    moveTo(pointX1, pointY1)
    lineTo(pointX2, pointY2)
    lineTo(pointX3, pointY3)
    lineTo(pointX4, pointY4)
    lineTo(pointX1, pointY1)
    fill()
    closePath()

}
