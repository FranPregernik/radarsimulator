package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.animation.*
import javafx.event.*
import javafx.geometry.*
import javafx.scene.*
import javafx.scene.canvas.*
import javafx.scene.image.*
import javafx.scene.input.*
import javafx.scene.input.KeyEvent.*
import javafx.scene.input.MouseEvent.*
import javafx.scene.paint.*
import javafx.scene.shape.*
import javafx.scene.transform.*
import javafx.util.*
import jfxtras.labs.util.*
import jfxtras.labs.util.event.*
import tornadofx.*
import java.lang.Math.*
import java.nio.*
import java.nio.channels.*
import java.text.*
import java.util.*
import java.util.Spliterators.*
import java.util.stream.*
import java.util.stream.StreamSupport.*
import kotlin.Pair
import kotlin.experimental.*

const val TWO_PI = 2 * PI
const val HALF_PI = PI / 2
const val S_TO_US = 1000.0 * 1000.0
const val MIN_TO_S = 60
const val MIN_TO_US = 60.0 * S_TO_US
const val HOUR_TO_US = 60.0 * MIN_TO_US
const val SPEED_OF_LIGHT_KM_US = 300000.0
const val FILE_HEADER_BYTE_CNT = 5 * 4

fun angleToAzimuth(angleRadians: Double): Double {
    return HALF_PI - angleRadians
}

fun azimuthToAngle(azimuthRadians: Double): Double {
    return HALF_PI - azimuthRadians
}

const val LIGHTSPEED_US_TO_ROUNDTRIP_KM = 2.0 / SPEED_OF_LIGHT_KM_US * S_TO_US

class RasterIterator(img: Image) : Iterator<Point2D> {

    val reader: PixelReader = img.pixelReader
    val pixelCnt = (img.width * img.height).toInt()
    var idx = 0

    val width = img.width
    val height = img.height

    override fun hasNext(): Boolean = idx in 0..(pixelCnt - 1)

    override fun next(): Point2D {

        while (hasNext()) {

            val x = idx % width
            // invert Y to convert to geometric coordinate system
            val y = idx / width

            idx += 1

            val color = reader.getColor(x.toInt(), y.toInt())
            if (!(color.red == 0.0 && color.green == 0.0 && color.blue == 0.0)) {
                return Point2D(x, height - 1 - y)
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

    @Suppress("UNUSED_PARAMETER")
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

            val intensity = (color.red + color.green + color.blue) / 3.0 * color.opacity
            if (intensity < 0.2) {
                color = Color.TRANSPARENT
            } else {
                color
            }

            writer.setColor(x, y, color)
        }
    }

    return outputImage
}

/**
 * Helper debug function to convert radar hits format to an image for easier viewing.
 */
fun drawRadarHitImage(gc: GraphicsContext, hits: Pair<Int, Int>, cParams: CalculationParameters, combinedTransform: Transform) {

    val sweepIdx = hits.first
    val signalTimeUs = hits.second
    val distanceKm = signalTimeUs / LIGHTSPEED_US_TO_ROUNDTRIP_KM

    if (signalTimeUs < cParams.minSignalTimeUs || signalTimeUs > cParams.maxSignalTimeUs) {
        return
    }

    if (distanceKm < cParams.minRadarDistanceKm || distanceKm > cParams.maxRadarDistanceKm) {
        return
    }

    val sweepHeadingRad = sweepIdx * cParams.c1
    val angle = azimuthToAngle(sweepHeadingRad)
    val x = distanceKm * cos(angle)
    val y = distanceKm * sin(angle)
    val hitPoint = combinedTransform.transform(x, y)
    gc.fill = Styles.hitColor
    gc.fillOval(
        hitPoint.x - 1.5,
        hitPoint.y - 1.5,
        3.0,
        3.0
    )
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

class SecondsStringConverter : StringConverter<Number>() {

    companion object {
        val decimalFormat = DecimalFormat().apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
            isGroupingUsed = false
            decimalFormatSymbols = DECIMAL_SYMBOLS
        }
    }

    /** {@inheritDoc}  */
    override fun fromString(value: String?): Number? {
        val safeValue = value?.trim() ?: return null

        if (safeValue.isEmpty()) {
            return null
        }

        return decimalFormat.parse(safeValue).toDouble()
    }

    /** {@inheritDoc}  */
    override fun toString(value: Number?) = if (value == null) "" else decimalFormat.format(value)
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

data class CalculationParameters(var radarParameters: RadarParameters) {
    val maxRadarDistanceKm = radarParameters.maxRadarDistanceKm
    val minRadarDistanceKm = radarParameters.minRadarDistanceKm
    val distanceResolutionKm = radarParameters.distanceResolutionKm
    val horizontalAngleBeamWidthRad = toRadians(radarParameters.horizontalAngleBeamWidthDeg)
    val azimuthChangePulseCount = radarParameters.azimuthChangePulse
    val c1 = TWO_PI / azimuthChangePulseCount
    val maxSignalTimeUs = ceil(maxRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)
    val minSignalTimeUs = ceil(minRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)
    val maxImpulsePeriodUs = radarParameters.maxImpulsePeriodUs.toInt()
    val rotationTimeUs = radarParameters.seekTimeSec * S_TO_US
    val width = round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()
    val height = round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()
}

fun calculateClutterHits(hitRaster: RasterIterator, cParam: CalculationParameters): Stream<Pair<Int, Int>> {

    return stream(spliteratorUnknownSize(hitRaster, Spliterator.ORDERED), false)
        .flatMap flatMapHitRaster@ { hit ->

            val x = (hit.x - cParam.width / 2.0) * cParam.distanceResolutionKm
            val y = (hit.y - cParam.height / 2.0) * cParam.distanceResolutionKm

            val radarDistanceKm = sqrt(pow(x, 2.0) + pow(y, 2.0))
            if (radarDistanceKm < cParam.minRadarDistanceKm || radarDistanceKm > cParam.maxRadarDistanceKm) {
                return@flatMapHitRaster Stream.empty<Pair<Int, Int>>()
            }

            val signalTimeUs = round(radarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM).toInt()
            if (!(signalTimeUs > cParam.minSignalTimeUs && signalTimeUs < cParam.maxSignalTimeUs)) {
                return@flatMapHitRaster Stream.empty<Pair<Int, Int>>()
            }

            val cartesianAngleRad = atan2(y, x)
            val sweepHeadingRad = angleToAzimuth(cartesianAngleRad)
            val sweepIdx = (sweepHeadingRad / cParam.c1).toInt()

            return@flatMapHitRaster Stream.of(Pair(sweepIdx, signalTimeUs))
        }
}

fun calculatePointTargetHits(pathSegment: PathSegment,
                             minTimeUs: Double,
                             maxTimeUs: Double,
                             cParam: CalculationParameters): Stream<Pair<Int, Int>> {

    val rotTimeUs = cParam.rotationTimeUs.toInt()
    val stepTimeUs = if (pathSegment.vKmh > 0)
        HOUR_TO_US * 0.3 * cParam.distanceResolutionKm / pathSegment.vKmh
    else
        pathSegment.t2Us

    val startTimeUs = max(0.0, minTimeUs)
    val startAcpIdx = cParam.azimuthChangePulseCount * floor(startTimeUs / cParam.rotationTimeUs).toInt()

    return DoubleStream.iterate(startTimeUs) { it + stepTimeUs }
        .limit(max(ceil((maxTimeUs - startTimeUs) / stepTimeUs).toLong(), 1))
        .filter { it < maxTimeUs }
        .boxed()
        .flatMap flatMap@ { tUs ->

            val plotPos = pathSegment.getPositionForTime(tUs) ?: return@flatMap Stream.empty<Pair<Int, Int>>()

            // get the angle of the target (center point)
            val radarDistanceKm = plotPos.rKm
            if (radarDistanceKm < cParam.minRadarDistanceKm || radarDistanceKm > cParam.maxRadarDistanceKm) {
                return@flatMap Stream.empty<Pair<Int, Int>>()
            }
            val signalTimeUs = floor(radarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM).toInt()
            if (!(signalTimeUs > cParam.minSignalTimeUs && signalTimeUs < cParam.maxSignalTimeUs)) {
                return@flatMap Stream.empty<Pair<Int, Int>>()
            }

            val targetAzRad = toRadians(plotPos.azDeg)
            val sweepIdx = startAcpIdx + floor(targetAzRad / cParam.c1).toInt()

            // check for hits
            val tTarget0 = tUs.toInt()
            val tTarget1 = (tUs + stepTimeUs).toInt()
            val tAntenna0 = (minTimeUs + (targetAzRad - cParam.horizontalAngleBeamWidthRad) / TWO_PI * cParam.rotationTimeUs).toInt()
            val tAntenna1 = (minTimeUs + (targetAzRad + cParam.horizontalAngleBeamWidthRad) / TWO_PI * cParam.rotationTimeUs).toInt()
            val rotations = (maxTimeUs - tAntenna0).toInt() / rotTimeUs

            IntStream.range(0, rotations + 1)
                .boxed()
                .flatMap { rot ->

                    val tRotAnt0 = tAntenna0 + rot * rotTimeUs
                    val tRotAnt1 = tAntenna1 + rot * rotTimeUs
                    val match = max(tTarget0, tRotAnt0) <= min(tTarget1, tRotAnt1)

                    if (!match) {
                        Stream.empty<Pair<Int, Int>>()
                    } else {
                        // we got a hit
                        Stream.of(Pair(sweepIdx, signalTimeUs))
                    }
                }

        }
}

fun spreadHits(hits: Pair<Int, Int>, cParam: CalculationParameters): Stream<Pair<Int, Int>> {

    // convert angle resolution to ACP idx spread
    val sweepIdxSpread = (cParam.horizontalAngleBeamWidthRad * cParam.azimuthChangePulseCount / TWO_PI).toInt()

    val sweepIdx = hits.first
    val signalTimeUs = hits.second

    // spread by angle
    val fromAcpIdx = max(0, sweepIdx - sweepIdxSpread)
    val toAcpIdx = min(cParam.azimuthChangePulseCount - 1, sweepIdx + sweepIdxSpread)

    // spread by distance
    // response must be as long as the radar impulse signal duration
    val fromRspTime = signalTimeUs
    val toRspTime = min(
        signalTimeUs + cParam.radarParameters.impulseSignalUs.toInt() - 1,
        cParam.maxSignalTimeUs.toInt()
    )

    // set signal hits taking into account the impulse signal duration
    return IntStream.range(fromAcpIdx, toAcpIdx + 1)
        .boxed()
        .flatMap { idx ->
            IntStream.range(fromRspTime, toRspTime + 1)
                .boxed()
                .map { bit -> Pair(idx, bit) }
        }

}

/**
 * Test 1 type of target is azimuth indifferent unlike the distance which is taken into account.
 */
fun calculateTest1TargetHits(pathSegment: PathSegment,
                             minTimeUs: Double,
                             maxTimeUs: Double,
                             cParam: CalculationParameters): Stream<Pair<Int, Int>> {

    val stepTimeUs = if (pathSegment.vKmh > 0)
        HOUR_TO_US * 0.3 * cParam.distanceResolutionKm / pathSegment.vKmh
    else
        maxTimeUs

    val startAcpIdx = cParam.azimuthChangePulseCount * floor(minTimeUs / cParam.rotationTimeUs).toInt()

    return DoubleStream.iterate(minTimeUs) { it + stepTimeUs }
        .limit(ceil((maxTimeUs - minTimeUs) / stepTimeUs).toLong())
        .filter { it < maxTimeUs }
        .boxed()
        .map { floor(it / cParam.rotationTimeUs) * cParam.rotationTimeUs }
        .distinct()
        .flatMap flatMap@ { tUs ->

            val plotPos = pathSegment.getPositionForTime(tUs) ?: return@flatMap Stream.empty<Pair<Int, Int>>()

            // get the angle of the target
            val radarDistanceKm = plotPos.rKm
            if (radarDistanceKm < cParam.minRadarDistanceKm || radarDistanceKm > cParam.maxRadarDistanceKm) {
                return@flatMap Stream.empty<Pair<Int, Int>>()
            }
            val signalTimeUs = round(radarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM).toInt()
            if (!(signalTimeUs > cParam.minSignalTimeUs && signalTimeUs < cParam.maxSignalTimeUs)) {
                return@flatMap Stream.empty<Pair<Int, Int>>()
            }

            return@flatMap IntStream.range(startAcpIdx, startAcpIdx + cParam.azimuthChangePulseCount)
                .boxed()
                .map { Pair(it, signalTimeUs) }

        }
}

/**
 * Test 2 type of target is distance indifferent unlike the azimuth which is taken into account.
 * Radar range is taken into account.
 */
fun calculateTest2TargetHits(pathSegment: PathSegment,
                             minTimeUs: Double,
                             maxTimeUs: Double,
                             cParam: CalculationParameters): Stream<Pair<Int, Int>> {

    val rotTimeUs = cParam.rotationTimeUs.toInt()
    val stepTimeUs = if (pathSegment.vKmh > 0)
        HOUR_TO_US * 0.3 * cParam.distanceResolutionKm / pathSegment.vKmh
    else
        maxTimeUs

    val startAcpIdx = cParam.azimuthChangePulseCount * floor(minTimeUs / cParam.rotationTimeUs).toInt()

    return DoubleStream.iterate(minTimeUs) { it + stepTimeUs }
        .limit(ceil((maxTimeUs - minTimeUs) / stepTimeUs).toLong())
        .filter { it < maxTimeUs }
        .boxed()
        .flatMap flatMap@ { tUs ->

            val plotPos = pathSegment.getPositionForTime(tUs) ?: return@flatMap Stream.empty<Pair<Int, Int>>()

            // get the angle of the target
            val radarDistanceKm = plotPos.rKm
            if (radarDistanceKm < cParam.minRadarDistanceKm || radarDistanceKm > cParam.maxRadarDistanceKm) {
                return@flatMap Stream.empty<Pair<Int, Int>>()
            }
            val signalTimeUs = round(radarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM).toInt()
            if (!(signalTimeUs > cParam.minSignalTimeUs && signalTimeUs < cParam.maxSignalTimeUs)) {
                return@flatMap Stream.empty<Pair<Int, Int>>()
            }

            val targetAzRad = toRadians(plotPos.azDeg)
            val sweepIdx = startAcpIdx + round(targetAzRad / cParam.c1).toInt()

            // check for hits
            val tTarget0 = tUs.toInt()
            val tTarget1 = (tUs + stepTimeUs).toInt()
            val tAntenna0 = (minTimeUs + (targetAzRad - cParam.horizontalAngleBeamWidthRad) / TWO_PI * cParam.rotationTimeUs).toInt()
            val tAntenna1 = (minTimeUs + (targetAzRad + cParam.horizontalAngleBeamWidthRad) / TWO_PI * cParam.rotationTimeUs).toInt()
            val rotations = (maxTimeUs - tAntenna0).toInt() / rotTimeUs

            IntStream.range(0, rotations + 1)
                .boxed()
                .flatMap { rot ->

                    val tRotAnt0 = tAntenna0 + rot * rotTimeUs
                    val tRotAnt1 = tAntenna1 + rot * rotTimeUs
                    val match = max(tTarget0, tRotAnt0) <= min(tTarget1, tRotAnt1)

                    if (!match) {
                        Stream.empty<Pair<Int, Int>>()
                    } else {
                        // we got a hit
                        IntStream.range(cParam.minSignalTimeUs.toInt(), cParam.maxSignalTimeUs.toInt() + 1)
                            .boxed()
                            .map { Pair(sweepIdx, it) }
                    }
                }
        }
}


fun calculateCloudTargetHits(pathSegment: PathSegment,
                             minTimeUs: Double,
                             maxTimeUs: Double,
                             hitRaster: RasterIterator,
                             cParam: CalculationParameters): Stream<Pair<Int, Int>> {

    val stepTimeUs = if (pathSegment.vKmh > 0)
        HOUR_TO_US * 0.3 * cParam.distanceResolutionKm / pathSegment.vKmh
    else
        maxTimeUs

    val startAcpIdx = cParam.azimuthChangePulseCount * floor(minTimeUs / cParam.rotationTimeUs).toInt()

    return DoubleStream.iterate(minTimeUs) { it + stepTimeUs }
        .limit(ceil((maxTimeUs - minTimeUs) / stepTimeUs).toLong())
        .filter { it < maxTimeUs }
        .boxed()
        .flatMap { tUs ->

            val plotPos = pathSegment.getPositionForTime(tUs) ?: return@flatMap Stream.empty<Pair<Int, Int>>()
            val cp = plotPos.toCartesian()

            stream(spliteratorUnknownSize(hitRaster, Spliterator.ORDERED), false)
                .flatMap flatMapHitRaster@ { hit ->

                    val x = cp.x + (hit.x - hitRaster.width / 2.0)
                    val y = cp.y + (hit.y - hitRaster.height / 2.0)

                    val radarDistanceKm = sqrt(pow(x, 2.0) + pow(y, 2.0))
                    if (radarDistanceKm < cParam.minRadarDistanceKm || radarDistanceKm > cParam.maxRadarDistanceKm) {
                        return@flatMapHitRaster Stream.empty<Pair<Int, Int>>()
                    }
                    val signalTimeUs = round(radarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM).toInt()
                    if (!(signalTimeUs > cParam.minSignalTimeUs && signalTimeUs < cParam.maxSignalTimeUs)) {
                        return@flatMapHitRaster Stream.empty<Pair<Int, Int>>()
                    }

                    val cartesianAngleRad = atan2(y, x)
                    val sweepHeadingRad = angleToAzimuth(cartesianAngleRad)

                    val minSweepIndex = startAcpIdx + floor((sweepHeadingRad - cParam.horizontalAngleBeamWidthRad) / cParam.c1).toInt()
                    val maxSweepIndex = startAcpIdx + ceil((sweepHeadingRad + cParam.horizontalAngleBeamWidthRad) / cParam.c1).toInt()

                    return@flatMapHitRaster IntStream.range(minSweepIndex, maxSweepIndex + 1)
                        .boxed()
                        .map { Pair(it, signalTimeUs) }
                }
        }
}

fun Image.getRasterHitMap(): RasterIterator {
    return RasterIterator(this)
}


fun setupViewPort(fromViewPort: Bounds, destViewPort: Bounds): Transform {

    val affine = Affine()

    val centerViewX = (fromViewPort.minX + fromViewPort.maxX) / 2.0
    val centerViewY = (fromViewPort.minY + fromViewPort.maxY) / 2.0

    val calcScale = min(destViewPort.width / fromViewPort.width, destViewPort.height / fromViewPort.height)
    val displayScale = if (calcScale.isNaN() || calcScale == 0.0) 1.0 else calcScale

    val centerDestX = (destViewPort.minX + destViewPort.maxX) / 2.0
    val centerDestY = (destViewPort.minY + destViewPort.maxY) / 2.0

    affine.appendTranslation(centerDestX, centerDestY)
    affine.appendScale(displayScale, -displayScale)
    affine.appendTranslation(-centerViewX, -centerViewY)

    return affine
}

/**
 * Incorporated from https://github.com/francisvalero/canvaskt
 */
class Sketch(f: Sketch.() -> Unit) {
    var nano: Long = 0
        internal set(value) {
            field = value
        }
    var mouseX: Double = 0.0
        internal set(value) {
            field = value
        }
    var mouseY: Double = 0.0
        internal set(value) {
            field = value
        }
    var isMousePressed: Boolean = false
        internal set(value) {
            field = value
        }
    var width: Double = 0.0
        internal set(value) {
            field = value
        }
    var height: Double = 0.0
        internal set(value) {
            field = value
        }


    private var fDraw: (GraphicsContext.() -> Unit) = {}
    private var fSetup: (GraphicsContext.() -> Unit) = {}
    private var fMousePressed: (GraphicsContext.(MouseEvent) -> Unit) = {}
    private var fMouseReleased: (GraphicsContext.(MouseEvent) -> Unit) = {}
    private var fMouseMoved: (GraphicsContext.(MouseEvent) -> Unit) = {}
    private var fMouseClicked: (GraphicsContext.(MouseEvent) -> Unit) = {}
    private var fMouseEntered: (GraphicsContext.(MouseEvent) -> Unit) = {}
    private var fMouseExited: (GraphicsContext.(MouseEvent) -> Unit) = {}
    private var fMouseDragged: (GraphicsContext.(MouseEvent) -> Unit) = {}

    private var fKeyPressed: (GraphicsContext.(KeyEvent) -> Unit) = {}
    private var fKeyReleased: (GraphicsContext.(KeyEvent) -> Unit) = {}
    private var fKeyTyped: (GraphicsContext.(KeyEvent) -> Unit) = {}

    init {
        f()
    }

    fun draw(g: GraphicsContext.() -> Unit) {
        fDraw = g
    }

    fun setup(g: GraphicsContext.() -> Unit) {
        fSetup = g
    }

    fun mousePressed(g: GraphicsContext.(MouseEvent) -> Unit) {
        fMousePressed = g
    }

    fun mouseReleased(g: GraphicsContext.(MouseEvent) -> Unit) {
        fMouseReleased = g
    }

    fun mouseMoved(g: GraphicsContext.(MouseEvent) -> Unit) {
        fMouseMoved = g
    }

    fun mouseClicked(g: GraphicsContext.(MouseEvent) -> Unit) {
        fMouseClicked = g
    }

    fun mouseEntered(g: GraphicsContext.(MouseEvent) -> Unit) {
        fMouseEntered = g
    }

    fun mouseExited(g: GraphicsContext.(MouseEvent) -> Unit) {
        fMouseExited = g
    }

    fun mouseDragged(g: GraphicsContext.(MouseEvent) -> Unit) {
        fMouseDragged = g
    }

    fun keyPressed(g: GraphicsContext.(KeyEvent) -> Unit) {
        fKeyPressed = g
    }

    fun keyReleased(g: GraphicsContext.(KeyEvent) -> Unit) {
        fKeyReleased = g
    }

    fun keyTyped(g: GraphicsContext.(KeyEvent) -> Unit) {
        fKeyTyped = g
    }

    internal fun setup(g: GraphicsContext) = g.fSetup()
    internal fun draw(g: GraphicsContext) = g.fDraw()
    internal fun mousePressed(g: GraphicsContext, e: MouseEvent) = g.fMousePressed(e)
    internal fun mouseReleased(g: GraphicsContext, e: MouseEvent) = g.fMouseReleased(e)
    internal fun mouseClicked(g: GraphicsContext, e: MouseEvent) = g.fMouseClicked(e)
    internal fun mouseMoved(g: GraphicsContext, e: MouseEvent) = g.fMouseMoved(e)
    internal fun mouseEntered(g: GraphicsContext, e: MouseEvent) = g.fMouseEntered(e)
    internal fun mouseExited(g: GraphicsContext, e: MouseEvent) = g.fMouseExited(e)
    internal fun mouseDragged(g: GraphicsContext, e: MouseEvent) = g.fMouseDragged(e)
    internal fun keyPressed(g: GraphicsContext, e: KeyEvent) = g.fKeyPressed(e)
    internal fun keyReleased(g: GraphicsContext, e: KeyEvent) = g.fKeyReleased(e)
    internal fun keyTyped(g: GraphicsContext, e: KeyEvent) = g.fKeyTyped(e)
}

/**
 * Incorporated from https://github.com/francisvalero/canvaskt
 */
class Canvas2D(val sketch: Sketch) : Canvas() {
    constructor(sketch: Sketch, w: Double, h: Double) : this(sketch) {
        width = w
        height = h
        isFocused = true
        isFocusTraversable = true
    }

    init {
        addEventFilter(MouseEvent.ANY) {
            sketch.mouseX = it.x
            sketch.mouseY = it.y
            when (it.eventType) {
                MOUSE_MOVED -> {
                    sketch.mouseMoved(graphicsContext2D, it)
                }
                MOUSE_PRESSED -> {
                    sketch.mousePressed(graphicsContext2D, it)
                    sketch.isMousePressed = true
                }
                MOUSE_RELEASED -> {
                    sketch.mouseReleased(graphicsContext2D, it)
                    sketch.isMousePressed = false
                }
                MOUSE_CLICKED -> {
                    sketch.mouseClicked(graphicsContext2D, it)
                }
                MOUSE_ENTERED -> {
                    sketch.mouseEntered(graphicsContext2D, it)
                }
                MOUSE_EXITED -> {
                    sketch.mouseExited(graphicsContext2D, it)
                }
                MOUSE_DRAGGED -> {
                    sketch.mouseDragged(graphicsContext2D, it)
                }
            }
        }
        addEventFilter(KeyEvent.ANY) {
            when (it.eventType) {
                KEY_PRESSED -> sketch.keyPressed(graphicsContext2D, it)
                KEY_RELEASED -> sketch.keyReleased(graphicsContext2D, it)
                KEY_TYPED -> sketch.keyTyped(graphicsContext2D, it)
            }
        }
        widthProperty().addListener { _, _, _ -> sketch.width = width }
        heightProperty().addListener { _, _, _ -> sketch.height = height }
    }

    val timer = object : AnimationTimer() {
        override fun handle(now: Long) {
            sketch.nano = now - startNano
            sketch.draw(graphicsContext2D)
        }
    }

    var startNano = 0L

    fun start() {
        startNano = System.nanoTime()
        sketch.setup(graphicsContext2D)
        timer.start()
    }

    fun stop() = timer.stop()
}


fun writeHitStream(raf: SeekableByteChannel,
                   hitStream: Stream<Pair<Int, Int>>,
                   radarParameters: RadarParameters,
                   rotations: Int) {


    val acpByteCnt = ceil(radarParameters.maxImpulsePeriodUs / 8).toInt()
    var actualRotations = 0

    // write hits
    val buffer = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN)
    hitStream.forEach { pair ->

        actualRotations = max(actualRotations, pair.first / radarParameters.azimuthChangePulse)

        val bytePos = pair.second / 8
        val bitPos = pair.second % 8
        val filePos = (FILE_HEADER_BYTE_CNT + pair.first * acpByteCnt + bytePos).toLong()

        // read full ACP
        buffer.clear()
        raf.position(filePos)
        raf.read(buffer)
        buffer.put(
            0,
            buffer[0] or (1 shl bitPos).toByte()
        )
        buffer.flip()
        raf.position(filePos)
        raf.write(buffer)

//        val seekTime = (pair.first / radarParameters.azimuthChangePulse) * radarParameters.seekTimeSec
//            updateProgress(
//                seekTime / (designerController.scenario.simulationDurationMin * MIN_TO_S),
//                1.0
//            )
    }

    // write header
    val headerBuffer = ByteBuffer.allocate(FILE_HEADER_BYTE_CNT)
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt((radarParameters.seekTimeSec * S_TO_US).toInt())
        .putInt(radarParameters.azimuthChangePulse)
        .putInt(radarParameters.impulsePeriodUs.toInt())
        .putInt(radarParameters.maxImpulsePeriodUs.toInt())
        .putInt(max(actualRotations, rotations))
    headerBuffer.flip()

    raf.position(0)
    raf.write(headerBuffer)

    // ensure file size is = header size + multiple of ARP block size
    if (actualRotations > 0) {
        val arpBlockSize = acpByteCnt * actualRotations
        raf.position(FILE_HEADER_BYTE_CNT.toLong() + arpBlockSize)
        raf.write(ByteBuffer.wrap(kotlin.ByteArray(1)))
    }
}