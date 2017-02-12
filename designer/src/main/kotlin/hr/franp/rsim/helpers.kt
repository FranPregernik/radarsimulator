package hr.franp.rsim

import javafx.event.*
import javafx.scene.*
import javafx.scene.image.*
import javafx.scene.input.*
import javafx.scene.shape.*
import javafx.util.*
import jfxtras.labs.util.*
import jfxtras.labs.util.event.*
import tornadofx.*
import java.text.*
import kotlin.Pair

const val TWO_PI = 2 * Math.PI
const val HALF_PI = Math.PI / 2
const val S_TO_US = 1000.0 * 1000.0
const val MIN_TO_S = 60
const val MIN_US = 60.0 * S_TO_US
const val HOUR_US = 60.0 * MIN_US
const val SPEED_OF_LIGHT_KM_US = 300000.0

fun angleToAzimuth(angleRadians: Double): Double {
    return HALF_PI - angleRadians
}

fun azimuthToAngle(azimuthRadians: Double): Double {
    return HALF_PI - azimuthRadians
}

const val LIGHTSPEED_US_TO_ROUNDTRIP_KM = 2.0 / SPEED_OF_LIGHT_KM_US * S_TO_US

fun hitToBitIndex(azimuthChangePulseCount: Int, rotationTimeUs: Double, maxDistanceKm: Double, t: Double, d: Double): Long {
    val maxSignalTimeUs = Math.ceil(2 * maxDistanceKm / SPEED_OF_LIGHT_KM_US * S_TO_US)
    val maxSignalBits = 32 * Math.ceil(maxSignalTimeUs / 32.0).toInt()
    val sweepIdx = Math.floor(azimuthChangePulseCount / rotationTimeUs * t).toLong()
    val signalTimeUs = Math.round(LIGHTSPEED_US_TO_ROUNDTRIP_KM * d)
    return maxSignalBits * sweepIdx + signalTimeUs
}

class Raster(byteArray: ByteArray, width: Int, height: Int) : Iterator<Pair<Int, Int>> {
    val hitIterator: Iterator<Pair<Int, Int>>

    init {
        val zeroByte = 0.toByte()

        @Suppress("UNCHECKED_CAST")
        hitIterator = byteArray
            .mapIndexed { i, byte ->
                val x = i % width
                val y = height - i / width // image(computer screen) y coordinate is the opposite if the math/geometry definition
                if (byte == zeroByte)
                    null
                else
                    Pair(x, y)
            }
            .filter { it != null }
            .iterator() as Iterator<Pair<Int, Int>>
    }

    override fun hasNext(): Boolean = hitIterator.hasNext()

    override fun next(): Pair<Int, Int> {
        return hitIterator.next()
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

    val hitColor = Styles.stationaryTargetColor
    val intColor = ((255 * hitColor.red).toInt() shl 16) + ((255 * hitColor.green).toInt() shl 8) + (255 * hitColor.blue).toInt()

    for (y in 0..(inputImage.height - 1).toInt()) {
        for (x in 0..(inputImage.width - 1).toInt()) {
            var argb = reader.getArgb(x, y)

            val r = argb shr 16 and 0xFF
            val g = argb shr 8 and 0xFF
            val b = argb and 0xFF

            if (r <= 127
                && g <= 127
                && b <= 127) {
                argb = 0x00000000
            } else {
                argb = 0xFF000000.toInt() + intColor
            }

            writer.setArgb(x, y, argb)
        }
    }

    return outputImage
}

class DistanceStringConverter : StringConverter<Double>() {

    companion object {
        val decimalFormat = DecimalFormat(".#")
    }

    /** {@inheritDoc}  */
    override fun fromString(value: String?): Double? {
        val safeValue = value?.trim() ?: return null

        if (safeValue.isEmpty()) {
            return null
        }

        return safeValue.toDouble()
    }

    /** {@inheritDoc}  */
    override fun toString(value: Double?) = if (value == null) "" else decimalFormat.format(value)
}

class AngleStringConverter : StringConverter<Double>() {

    companion object {
        val decimalFormat = DecimalFormat().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
    }

    /** {@inheritDoc}  */
    override fun fromString(value: String?): Double? {
        val safeValue = value?.trim() ?: return null

        if (safeValue.isEmpty()) {
            return null
        }

        return safeValue.toDouble()
    }

    /** {@inheritDoc}  */
    override fun toString(value: Double?) = if (value == null) "" else decimalFormat.format(value)
}

class SpeedStringConverter : StringConverter<Double>() {

    companion object {
        val decimalFormat = DecimalFormat(".#")
    }

    /** {@inheritDoc}  */
    override fun fromString(value: String?): Double? {
        val safeValue = value?.trim() ?: return null

        if (safeValue.isEmpty()) {
            return null
        }

        return safeValue.toDouble()
    }

    /** {@inheritDoc}  */
    override fun toString(value: Double?) = if (value == null) "" else decimalFormat.format(value)

}
