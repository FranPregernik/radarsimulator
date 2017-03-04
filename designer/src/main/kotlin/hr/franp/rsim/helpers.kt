package hr.franp.rsim

import hr.franp.*
import hr.franp.rsim.models.*
import javafx.event.*
import javafx.geometry.*
import javafx.scene.*
import javafx.scene.image.*
import javafx.scene.input.*
import javafx.scene.paint.*
import javafx.scene.shape.*
import javafx.util.*
import jfxtras.labs.util.*
import jfxtras.labs.util.event.*
import tornadofx.*
import java.lang.Math.*
import java.text.*


const val TWO_PI = 2 * PI
const val HALF_PI = PI / 2
const val S_TO_US = 1000.0 * 1000.0
const val MIN_TO_S = 60
const val MIN_TO_US = 60.0 * S_TO_US
const val HOUR_TO_US = 60.0 * MIN_TO_US
const val SPEED_OF_LIGHT_KM_US = 300000.0

fun angleToAzimuth(angleRadians: Double): Double {
    return HALF_PI - angleRadians
}

fun azimuthToAngle(azimuthRadians: Double): Double {
    return HALF_PI - azimuthRadians
}

const val LIGHTSPEED_US_TO_ROUNDTRIP_KM = 2.0 / SPEED_OF_LIGHT_KM_US * S_TO_US

class RasterIterator(val img: Image) : Iterator<Point2D> {

    val reader: PixelReader = img.pixelReader
    val pixelCnt = (img.width * img.height).toInt()
    var idx = 0

    override fun hasNext(): Boolean = idx in 0..(pixelCnt - 1)

    override fun next(): Point2D {

        while (hasNext()) {

            val x = idx % img.width
            // invert Y to convert to geometric coordinate system
            val y = idx / img.width

            idx += 1

            val color = reader.getColor(x.toInt(), y.toInt())
            if (!(color.red == 0.0 && color.green == 0.0 && color.blue == 0.0)) {
                return Point2D(x, img.height - 1 - y)
            }

        }

        return Point2D.ZERO


    }

}


/**
 * Adds a selection rectangle gesture to the specified parent node.
 * A rectangle node must be specified that is used to indicate the selection
 * area.
 *
 * @param root parent node
 * *
 * @param rect selection rectangle
 * *
 * @param releaseHandler additional release handler (optional, may be `null`)
 * *
 * *
 * Most of the code from jfxtras MouseControlUtils adapted to my needs
 */
fun addSelectionRectangleGesture(root: Parent,
                                 rect: Rectangle,
                                 releaseHandler: EventHandler<MouseEvent>?): RectangleSelectionController {

    val releaseHandlerGroup = EventHandlerGroup<MouseEvent>()

    if (releaseHandler != null) {
        releaseHandlerGroup.addHandler(releaseHandler)
    }

    return RectangleSelectionController(root, rect, releaseHandlerGroup)

}

/**
 * Most of the code from jfxtras MouseControlUtils adapted to my needs
 */
class RectangleSelectionController(private val root: Parent,
                                   private val rectangle: Rectangle,
                                   releasedEvtHandler: EventHandlerGroup<MouseEvent>) {

    private var firstX: Double = 0.toDouble()
    private var firstY: Double = 0.toDouble()
    private var secondX: Double = 0.toDouble()
    private var secondY: Double = 0.toDouble()

    init {

        val originalOnMouseDragged = root.onMouseDragged
        root.onMouseDragged = EventHandlerGroup<MouseEvent>().apply {
            if (originalOnMouseDragged != null) {
                addHandler { event -> originalOnMouseDragged.handle(event) }
            }
            addHandler { event ->
                if (rectangle.parent == root) {
                    performDrag(root, event)
                }
                event.consume()
            }
        }

        val originalOnMousePressed = root.onMousePressed
        root.onMousePressed = EventHandlerGroup<MouseEvent>().apply {
            if (originalOnMousePressed != null) {
                addHandler { event -> originalOnMousePressed.handle(event) }
            }
            addHandler { event ->
                if (event.isSecondaryButtonDown) {
                    performDragBegin(root, event)
                }
                event.consume()
            }
        }


        val originalOnMouseReleased = root.onMouseReleased
        root.onMouseReleased = EventHandlerGroup<MouseEvent>().apply {
            if (originalOnMouseReleased != null) {
                addHandler { event -> originalOnMouseReleased.handle(event) }
            }
            addHandler { event ->
                if (rectangle.parent == root) {
                    performDragEnd(root, event)
                    event.consume()
                }
            }
            addHandler(releasedEvtHandler)
        }

    }

    fun performDrag(root: Parent, event: MouseEvent) {
        val parentScaleX = root.localToSceneTransformProperty().value.mxx
        val parentScaleY = root.localToSceneTransformProperty().value.myy

        secondX = event.sceneX
        secondY = event.sceneY

        firstX = max(firstX, 0.0)
        firstY = max(firstY, 0.0)

        secondX = max(secondX, 0.0)
        secondY = max(secondY, 0.0)

        val x = min(firstX, secondX)
        val y = min(firstY, secondY)

        val width = abs(secondX - firstX)
        val height = abs(secondY - firstY)

        rectangle.x = x / parentScaleX
        rectangle.y = y / parentScaleY
        rectangle.width = width / parentScaleX
        rectangle.height = height / parentScaleY
    }

    fun performDragBegin(root: Parent, event: MouseEvent) {
        if (rectangle.parent != null) {
            return
        }

        // record the current mouse X and Y position on Node
        firstX = event.sceneX
        firstY = event.sceneY

        NodeUtil.addToParent(root, rectangle)

        rectangle.width = 0.0
        rectangle.height = 0.0

        rectangle.x = firstX
        rectangle.y = firstY

        rectangle.toFront()

    }

    fun performDragEnd(root: Parent, event: MouseEvent) {
        rectangle.removeFromParent()
    }
}


fun processHitMaskImage(img: Image): Image {

    val outputImage = WritableImage(img.width.toInt(), img.height.toInt())
    val reader = img.pixelReader
    val writer = outputImage.pixelWriter

    for (y in 0..(img.height - 1).toInt()) {
        for (x in 0..(img.width - 1).toInt()) {
            var color = reader.getColor(x, y)

            if (color.red == 0.0 && color.green == 0.0 && color.blue == 0.0) {
                color = Color.TRANSPARENT
            }

            writer.setColor(x, y, color)
        }
    }

    return outputImage
}

/**
 * Helper debug function to convert radar hits format to an image for easier viewing.
 */
fun generateRadarHitImage(hits: Bits, radarParameters: RadarParameters): WritableImage {

    val outputImage = WritableImage(
        2 * radarParameters.maxRadarDistanceKm.toInt(),
        2 * radarParameters.maxRadarDistanceKm.toInt()
    )
    val writer = outputImage.pixelWriter

    val c1 = TWO_PI / radarParameters.azimuthChangePulse
    val maxImpulsePeriodUs = radarParameters.maxImpulsePeriodUs.toInt()

    var idx = hits.nextSetBit(0)
    while (idx >= 0 && idx < hits.size()) {

        val sweepIdx = idx / maxImpulsePeriodUs
        val signalTimeUs = idx % maxImpulsePeriodUs

        val sweepHeadingRad = sweepIdx * c1
        val distanceKm = signalTimeUs / LIGHTSPEED_US_TO_ROUNDTRIP_KM

        val angle = azimuthToAngle(sweepHeadingRad)
        val x = (radarParameters.maxRadarDistanceKm + distanceKm * cos(angle)).toInt()
        val y = (2 * radarParameters.maxRadarDistanceKm - (radarParameters.maxRadarDistanceKm + distanceKm * sin(angle))).toInt()

        writer.setColor(x, y, Color.RED)

        idx = hits.nextSetBit(idx + 1)

    }

    return outputImage
}

val DECIMAL_SYMBOLS = DecimalFormatSymbols().apply {
    decimalSeparator = '.'
    groupingSeparator = ','
}

class DistanceStringConverter : StringConverter<Double>() {

    companion object {
        val decimalFormat = DecimalFormat().apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
            isGroupingUsed = false
            decimalFormatSymbols = DECIMAL_SYMBOLS
        }
    }

    /** {@inheritDoc}  */
    override fun fromString(value: String?): Double? {
        val safeValue = value?.trim() ?: return null

        if (safeValue.isEmpty()) {
            return null
        }

        return decimalFormat.parse(safeValue).toDouble()
    }

    /** {@inheritDoc}  */
    override fun toString(value: Double?) = if (value == null) "" else decimalFormat.format(value)
}

class AngleStringConverter : StringConverter<Double>() {

    companion object {
        val decimalFormat = DecimalFormat().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
            isGroupingUsed = false
            decimalFormatSymbols = DECIMAL_SYMBOLS
        }
    }


    /** {@inheritDoc}  */
    override fun fromString(value: String?): Double? {
        val safeValue = value?.trim() ?: return null

        if (safeValue.isEmpty()) {
            return null
        }

        return decimalFormat.parse(safeValue).toDouble()
    }

    /** {@inheritDoc}  */
    override fun toString(value: Double?): String = if (value == null) "" else decimalFormat.format(normalizeAngleDeg(value))
}

class SpeedStringConverter : StringConverter<Double>() {

    companion object {
        val decimalFormat = DecimalFormat().apply {
            minimumFractionDigits = 1
            maximumFractionDigits = 1
            isGroupingUsed = false
            decimalFormatSymbols = DECIMAL_SYMBOLS
        }
    }

    /** {@inheritDoc}  */
    override fun fromString(value: String?): Double? {
        val safeValue = value?.trim() ?: return null

        if (safeValue.isEmpty()) {
            return null
        }

        return decimalFormat.parse(safeValue).toDouble()
    }

    /** {@inheritDoc}  */
    override fun toString(value: Double?) = if (value == null) "" else decimalFormat.format(value)

}

fun normalizeAngleDeg(angle: Double): Double {
    return ((angle % 360) + 360) % 360
}

fun calculateClutterHits(hits: Bits, hitRaster: RasterIterator, radarParameters: RadarParameters) {
    val maxRadarDistanceKm = radarParameters.maxRadarDistanceKm
    val minRadarDistanceKm = radarParameters.minRadarDistanceKm
    val distanceResolutionKm = radarParameters.distanceResolutionKm

    val width = round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()
    val height = round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()

    val azimuthChangePulseCount = radarParameters.azimuthChangePulse
    val horizontalAngleBeamWidthRad = Math.toRadians(radarParameters.horizontalAngleBeamWidthDeg)
    val c1 = TWO_PI / azimuthChangePulseCount
    val maxSignalTimeUs = ceil(maxRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)
    val minSignalTimeUs = ceil(minRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)

    for (hit in hitRaster) {
        val x = (hit.x - width / 2.0) * distanceResolutionKm
        val y = (hit.y - height / 2.0) * distanceResolutionKm

        val radarDistanceKm = sqrt(pow(x, 2.0) + pow(y, 2.0))
        if (radarDistanceKm < minRadarDistanceKm || radarDistanceKm > maxRadarDistanceKm) {
            continue
        }

        val cartesianAngleRad = Math.atan2(y, x)
        val sweepHeadingRad = angleToAzimuth(cartesianAngleRad)

        val minSweepIndex = floor((sweepHeadingRad - horizontalAngleBeamWidthRad) / c1).toInt()
        val maxSweepIndex = ceil((sweepHeadingRad + horizontalAngleBeamWidthRad) / c1).toInt()

        for (sweepIdx in minSweepIndex..maxSweepIndex) {
            val signalTimeUs = round(radarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM).toInt()
            if (signalTimeUs > minSignalTimeUs && signalTimeUs < maxSignalTimeUs) {
                val normSweepIdx = ((sweepIdx % radarParameters.azimuthChangePulse) + radarParameters.azimuthChangePulse) % radarParameters.azimuthChangePulse
                val bitIdx = normSweepIdx * radarParameters.maxImpulsePeriodUs.toInt() + signalTimeUs

                // set signal hit
                hits.setBit(bitIdx, true)
            }
        }
    }
}

fun calculatePointTargetHits(hits: Bits, position: RadarCoordinate, tUs: Double, radarParameters: RadarParameters) {

    val maxRadarDistanceKm = radarParameters.maxRadarDistanceKm
    val minRadarDistanceKm = radarParameters.minRadarDistanceKm
    val horizontalAngleBeamWidthRad = toRadians(radarParameters.horizontalAngleBeamWidthDeg)
    val rotationTimeUs = radarParameters.seekTimeSec * S_TO_US
    val sweepHeadingRad = TWO_PI / rotationTimeUs * tUs
    val azimuthChangePulseCount = radarParameters.azimuthChangePulse
    val c1 = TWO_PI / azimuthChangePulseCount
    val maxSignalTimeUs = ceil(maxRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)
    val minSignalTimeUs = ceil(minRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)

    // get the angle of the target (center point)
    val radarDistanceKm = position.rKm
    if (radarDistanceKm < minRadarDistanceKm || radarDistanceKm > maxRadarDistanceKm) {
        return
    }

    val targetHeadingRad = toRadians(position.azDeg)
    val diff = abs(((abs(targetHeadingRad - sweepHeadingRad) + PI) % TWO_PI) - PI)
    if (diff > horizontalAngleBeamWidthRad / 2.0) {
        return
    }

    val sweepIdx = round(sweepHeadingRad / c1).toInt()
    val signalTimeUs = round(radarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM).toInt()
    if (signalTimeUs > minSignalTimeUs && signalTimeUs < maxSignalTimeUs) {
        val normSweepIdx = ((sweepIdx % radarParameters.azimuthChangePulse) + radarParameters.azimuthChangePulse) % radarParameters.azimuthChangePulse
        val bitIdx = normSweepIdx * radarParameters.maxImpulsePeriodUs.toInt() + signalTimeUs

        // set signal hit
        hits.setBit(bitIdx, true)
    }

}

fun calculateTestTargetHits(hits: Bits, position: RadarCoordinate, tUs: Double, radarParameters: RadarParameters) {
}

fun calculateCloudTargetHits(hits: Bits, position: RadarCoordinate, tUs: Double, radarParameters: RadarParameters) {

}