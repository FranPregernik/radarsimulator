package hr.franp.rsim.shapes

import hr.franp.rsim.*
import javafx.scene.*
import javafx.scene.canvas.*
import javafx.scene.image.*
import javafx.scene.paint.*
import javafx.scene.shape.*
import javafx.scene.text.*
import javafx.scene.transform.*
import tornadofx.*
import java.lang.Math.*

class DistanceMarkerCircle(displayScale: Double, r: Double) : Circle(0.0, 0.0, r, Color.TRANSPARENT) {
    init {
        addClass(Styles.distanceMarkerCircle)
        strokeWidth = 1.0 / displayScale
    }
}

class DistanceMarkerLabel(displayScale: Double, d: Double, text: String) : Text(d, 0.0, text) {
    init {
        addClass(Styles.distanceMarkerLabel)
        font = Font(Font.getDefault().size / displayScale)

        transforms.clear()
        transforms.add(Scale(1.0, -1.0))
        transforms.add(Translate(-boundsInLocal.width / 2, boundsInLocal.height))
    }
}

class AzimuthMarkerLabel(displayScale: Double, r: Double, angleRad: Double) : Text(
    r * cos(angleToAzimuth(angleRad) - HALF_PI),
    r * sin(angleToAzimuth(angleRad) - HALF_PI),
    ((720 + toDegrees(angleToAzimuth(angleRad))) % 360).toInt().toString()
) {
    init {
        addClass(Styles.azimuthMarkerLabel)
        font = Font(Font.getDefault().size / displayScale)

        transforms.clear()
        transforms.add(Scale(1.0, -1.0))
        transforms.add(Translate(-boundsInLocal.width / 2, 0.0))
    }
}

class AzimuthMarkerLine(displayScale: Double, x1: Double, y1: Double, x2: Double, y2: Double) : Line(x1, y1, x2, y2) {
    init {
        addClass(Styles.azimuthMarkerLine)
        strokeWidth = 1.0 / displayScale
    }
}

class MovingTargetCourseLine(displayScale: Double, x1: Double, y1: Double, x2: Double, y2: Double) : Line(x1, y1, x2, y2) {
    init {
        addClass(Styles.movingTargetCourseLine)
        strokeWidth = 1.0 / displayScale
    }
}

class MovingTargetPathMarker(displayScale: Double, x: Double, y: Double) : Circle(x, y, 1.0 / displayScale, Color.TRANSPARENT) {
    init {
        addClass(Styles.movingTargetPathMarker)
        strokeWidth = 1.0 / displayScale
    }
}

class MovingTargetPositionMarker(displayScale: Double, x: Double, y: Double, text: String, width: Double = 10.0, height: Double = 10.0, color: Color, image: Image? = null) : Group() {
    val label = Text(
        x,
        -y + height / 2 / displayScale + 1.5 * Font.getDefault().size / displayScale,
        text
    ).apply {
        addClass(Styles.movingTargetPositionLabel)
        font = Font(Font.getDefault().size / displayScale)

        transforms.clear()
        transforms.add(Scale(1.0, -1.0))
        transforms.add(Translate(-boundsInLocal.width / 2, 0.0))
    }

    val marker = Rectangle(
        x - width / 2.0 / displayScale,
        y - height / 2.0 / displayScale,
        width / displayScale,
        height / displayScale
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
                this.x = x - width / 2.0
                this.y = y - height / 2.0
                this.fitWidth = width
                this.fitHeight = height
                this.scaleY = -1.0
            })
        }
    }
}

class MovingTargetPlotMarker(displayScale: Double, x: Double, y: Double, r: Double = 2.0) : Circle(x, y, r / displayScale) {
    init {
        addClass(Styles.movingTargetPlotMarker)
    }
}

class Test1TargetPositionMarker(displayScale: Double, x: Double, y: Double, text: String, color: Color) : Group() {
    val label = Text(
        x,
        -y + 1.5 * Font.getDefault().size / displayScale,
        text
    ).apply {
        addClass(Styles.movingTargetPositionLabel)
        font = Font(Font.getDefault().size / displayScale)

        transforms.clear()
        transforms.add(Scale(1.0, -1.0))
        transforms.add(Translate(-boundsInLocal.width / 2, 0.0))
    }

    private val d = sqrt(pow(x, 2.0) + pow(y, 2.0))

    val marker = Circle(0.0, 0.0, d, Color.TRANSPARENT).apply {
        addClass(Styles.movingTargetTestOneCircle)
        strokeWidth = 1.0 / displayScale
        stroke = color
    }

    init {
        add(marker)
        add(label)
    }
}

class Test2TargetPositionMarker(displayScale: Double, x: Double, y: Double, text: String, angleResolutionDeg: Double, maxDistance: Double, color: Color) : Group() {
    val label = Text(
        x,
        -y + 1.5 * Font.getDefault().size / displayScale,
        text
    ).apply {
        addClass(Styles.movingTargetPositionLabel)
        font = Font(Font.getDefault().size / displayScale)

        transforms.clear()
        transforms.add(Scale(1.0, -1.0))
        transforms.add(Translate(-boundsInLocal.width / 2, 0.0))
    }

    private val startAngleDeg = 360 - floor(toDegrees(atan2(y, x)) / angleResolutionDeg) * angleResolutionDeg

    val marker = Arc(0.0, 0.0, maxDistance, maxDistance, startAngleDeg, angleResolutionDeg).apply {
        addClass(Styles.movingTargetTestTwoWedge)
        stroke = color
        strokeWidth = 1.0 / displayScale
        fill = color
        type = ArcType.ROUND
    }

    init {
        println(toDegrees(atan2(y, x)))
        println(startAngleDeg)
        add(marker)
        add(label)
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
