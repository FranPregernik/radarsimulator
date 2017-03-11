package hr.franp.rsim.shapes

import hr.franp.rsim.*
import javafx.geometry.*
import javafx.scene.*
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

class MovingTargetPositionMarker(p: Point2D, text: String, color: Color, image: Image? = null, imageBounds: Bounds? = null) : Group() {

    val width = (imageBounds?.width ?: 10.0)
    val height = (imageBounds?.height ?: 10.0)

    val label = Text(
        p.x,
        p.y + width / 2 + 1.5 * Font.getDefault().size,
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
                this.x = imageBounds?.minX ?: (p.x - width / 2.0)
                this.y = imageBounds?.minY ?: (p.y - height / 2.0)
                isPreserveRatio = true
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

class Test1TargetHitMarker(cp: Point2D, d: Double) : Group() {

    val marker = Circle(cp.x, cp.y, d, Color.TRANSPARENT).apply {
        addClass(Styles.movingTargetTestOnePlotCircle)
        strokeWidth = 1.0
    }

    init {
        addClass(Styles.movingTargetPlotMarker)
        add(marker)
    }
}

class Test2TargetHitMarker(cp: Point2D, azDeg: Double, angleResolutionDeg: Double, maxDistance: Double) : Group() {
    private val angleDeg = toDegrees(azimuthToAngle(toRadians(azDeg)))
    private val startAngleDeg = floor(angleDeg / angleResolutionDeg) * angleResolutionDeg

    val marker = Arc(cp.x, cp.y, maxDistance, maxDistance, startAngleDeg, angleResolutionDeg).apply {
        addClass(Styles.movingTargetTestTwoPlotWedge)
        strokeWidth = 1.0
        type = ArcType.ROUND
    }

    init {
        addClass(Styles.movingTargetPlotMarker)
        add(marker)
    }
}