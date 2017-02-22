package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.event.*
import javafx.scene.*
import javafx.scene.image.*
import javafx.scene.input.*
import javafx.scene.paint.*
import javafx.scene.shape.*
import javafx.util.*
import jfxtras.labs.util.*
import jfxtras.labs.util.event.*
import tornadofx.*
import java.text.*
import java.util.*
import kotlin.Pair


const val TWO_PI = 2 * Math.PI
const val HALF_PI = Math.PI / 2
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

class Raster(val bitSet: BitSet, val width: Int, val height: Int) : Iterator<Pair<Int, Int>> {

    var bitIndex = bitSet.nextSetBit(0)

    override fun hasNext(): Boolean = (bitIndex >= 0)

    override fun next(): Pair<Int, Int> {

        val x = bitIndex % width
        val y = height - bitIndex / width

        bitIndex = bitSet.nextSetBit(bitIndex + 1)

        return Pair(x, y)

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

        firstX = Math.max(firstX, 0.0)
        firstY = Math.max(firstY, 0.0)

        secondX = Math.max(secondX, 0.0)
        secondY = Math.max(secondY, 0.0)

        val x = Math.min(firstX, secondX)
        val y = Math.min(firstY, secondY)

        val width = Math.abs(secondX - firstX)
        val height = Math.abs(secondY - firstY)

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


fun processHitMaskImage(inputImage: Image): Image {
    val outputImage = WritableImage(inputImage.width.toInt(), inputImage.height.toInt())
    val reader = inputImage.pixelReader
    val writer = outputImage.pixelWriter

    for (y in 0..(inputImage.height - 1).toInt()) {
        for (x in 0..(inputImage.width - 1).toInt()) {
            var color = reader.getColor(x, y)

            if (color.red == 0.0 && color.green == 0.0 && color.blue == 0.0) {
                color = Color.TRANSPARENT
            }

            writer.setColor(x, y, color)
        }
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

fun calculatePointTargetHits(hits: BitSet, position: RadarCoordinate, tUs: Double, radarParameters: RadarParameters) {

    val maxRadarDistanceKm = radarParameters.maxRadarDistanceKm
    val minRadarDistanceKm = radarParameters.minRadarDistanceKm
    val horizontalAngleBeamWidthRad = Math.toRadians(radarParameters.horizontalAngleBeamWidthDeg)
    val rotationTimeUs = radarParameters.seekTimeSec * S_TO_US
    val sweepHeadingRad = TWO_PI / rotationTimeUs * tUs
    val azimuthChangePulseCount = radarParameters.azimuthChangePulse
    val c1 = TWO_PI / azimuthChangePulseCount
    val maxSignalTimeUs = Math.ceil(maxRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)
    val minSignalTimeUs = Math.ceil(minRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)

    // get the angle of the target (center point)
    val radarDistanceKm = position.rKm
    if (radarDistanceKm < minRadarDistanceKm || radarDistanceKm > maxRadarDistanceKm) {
        return
    }

    val targetHeadingRad = Math.toRadians(position.azDeg)
    val diff = Math.abs(((Math.abs(targetHeadingRad - sweepHeadingRad) + Math.PI) % TWO_PI) - Math.PI)
    if (diff > horizontalAngleBeamWidthRad / 2.0) {
        return
    }

    val sweepIdx = Math.round(sweepHeadingRad / c1).toInt()
    val signalTimeUs = Math.round(radarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM).toInt()
    if (signalTimeUs > minSignalTimeUs && signalTimeUs < maxSignalTimeUs) {
        // set signal hit
        hits.set((sweepIdx % radarParameters.azimuthChangePulse) * radarParameters.impulsePeriodUs.toInt() + signalTimeUs, true)
    }

}

fun calculateTestTargetHits(hits: BitSet, position: RadarCoordinate, tUs: Double, radarParameters: RadarParameters) {
}

fun calculateCloudTargetHits(hits: BitSet, position: RadarCoordinate, tUs: Double, radarParameters: RadarParameters) {

}