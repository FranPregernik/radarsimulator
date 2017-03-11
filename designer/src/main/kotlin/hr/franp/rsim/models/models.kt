package hr.franp.rsim.models

import hr.franp.rsim.*
import hr.franp.rsim.models.RadarCoordinate.Companion.fromCartesian
import javafx.collections.FXCollections.*
import javafx.embed.swing.*
import javafx.geometry.*
import javafx.scene.image.*
import tornadofx.*
import java.awt.image.*
import java.io.*
import java.lang.Math.*
import java.util.*
import javax.imageio.*
import javax.json.*


class RadarCoordinate() : JsonModel {

    companion object {
        fun fromCartesian(x: Double, y: Double): RadarCoordinate {
            return RadarCoordinate(
                sqrt(pow(x, 2.0) + pow(y, 2.0)),
                toDegrees(angleToAzimuth(atan2(y, x)))
            )
        }
    }

    /**
     * Distance from radar
     */
    var rKm by property<Double>()

    fun rProperty() = getProperty(RadarCoordinate::rKm)

    /**
     * Azimuth shift from radar north
     */
    var azDeg by property<Double>()

    fun azDegProperty() = getProperty(RadarCoordinate::azDeg)

    constructor(_rKm: Double, _azDeg: Double) : this() {
        rKm = _rKm
        azDeg = _azDeg
    }

    fun toCartesian(): Point2D {
        return Point2D(
            rKm * cos(azimuthToAngle(toRadians(azDeg))),
            rKm * sin(azimuthToAngle(toRadians(azDeg)))
        )
    }

    override fun toJSON(json: JsonBuilder) {
        with(json) {
            add("rKm", rKm)
            add("azDeg", azDeg)
        }
    }

    override fun updateModel(json: JsonObject) {
        with(json) {
            rKm = double("rKm")
            azDeg = double("azDeg")
        }
    }
}

class Direction(destination: RadarCoordinate = RadarCoordinate(), speedKmh: Double = 0.0) : JsonModel {
    var destination by property(destination)
    fun destinationProperty() = getProperty(Direction::destination)
    fun rProperty() = destination.rProperty()
    fun azProperty() = destination.azDegProperty()

    var speedKmh by property(speedKmh)
    fun speedKmhProperty() = getProperty(Direction::speedKmh)

    override fun toJSON(json: JsonBuilder) {
        with(json) {
            add("rKm", destination?.rKm)
            add("azDeg", destination?.azDeg)
            add("speedKmh", speedKmh)
        }
    }

    override fun updateModel(json: JsonObject) {
        with(json) {
            destination = RadarCoordinate(
                double("rKm") ?: 0.0,
                double("azDeg") ?: 0.0
            )
            speedKmh = double("speedKmh")
        }
    }
}

enum class MovingTargetType {
    /**
     * Simple point target like a plane, helicopter, etc.
     */
    Point,

    /**
     * One type of cloud
     */
    Cloud1,

    /**
     * Another type of cloud
     */
    Cloud2,

    /**
     * Target that appears on all azimuth (distance depends on the current course)
     */
    Test1,

    /**
     * Target that appears on all distances (azimuth depends on the current course)
     */
    Test2
}

class MovingTarget : JsonModel {
    var name by property("")
    fun nameProperty() = getProperty(MovingTarget::name)

    var type by property(MovingTargetType.Point)
    fun typeProperty() = getProperty(MovingTarget::type)

    var initialPosition by property(RadarCoordinate(0.0, 0.0))
    fun initialPositionProperty() = getProperty(MovingTarget::initialPosition)

    var directions by property(observableArrayList<Direction>(mutableListOf()))

    fun directionsProperty() = getProperty(MovingTarget::directions)

    override fun toString(): String {
        return name
    }

    override fun toJSON(json: JsonBuilder) {
        with(json) {
            add("name", name)
            add("type", type.toString())
            add("initialPosition", initialPosition.toJSON())
            add("directions", directions.toJSON())
        }
    }

    override fun updateModel(json: JsonObject) {
        with(json) {
            name = string("name")
            type = MovingTargetType.valueOf(string("type")!!)
            initialPosition = getJsonObject("initialPosition").toModel()
            directions = getJsonArray("directions")?.toModel() ?: observableArrayList<Direction>(mutableListOf())
        }
    }
}

class Clutter() : JsonModel {

    var bytes by property<ByteArray>()
    fun bytesProperty() = getProperty(Clutter::bytes)

    constructor(imageFile: File) : this() {
        bytes = imageFile.readBytes()
    }

    private fun scaleStoredImage(minHeight: Int, minWidth: Int): Image {

        val img: BufferedImage?
        val originalWidth: Double
        val originalHeight: Double

        if (bytes != null) {
            img = ImageIO.read(bytes.inputStream())
        } else {
            img = null
        }

        if (img != null) {
            originalWidth = img.width.toDouble()
            originalHeight = img.height.toDouble()
        } else {
            originalWidth = minWidth.toDouble()
            originalHeight = minHeight.toDouble()
        }

        // find the right scale factor to preserve ration as well as stretch
        // the image so it is at least minW x minH
        val scale = max(minWidth.toDouble() / originalWidth, minHeight.toDouble() / originalHeight)
        val width = (originalWidth * scale).toInt()
        val height = (originalHeight * scale).toInt()

        // create a BW version with the specified size
        val bwImage = BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY)

        // draw image if it exists
        if (img != null) {
            val g = bwImage.createGraphics()
            g.drawImage(img, 0, 0, width, height, null)
            g.dispose()
        }

        val fxImage = SwingFXUtils.toFXImage(bwImage, null)
        return fxImage
    }

    fun getImage(minWidth: Int, minHeight: Int): Image {
        val fxImage = scaleStoredImage(minHeight, minWidth)
        return processHitMaskImage(fxImage)
    }

    override fun toJSON(json: JsonBuilder) {
        with(json) {
            add("imageFileContents", Base64.getEncoder().encodeToString(bytes ?: ByteArray(0)))
        }
    }

    override fun updateModel(json: JsonObject) {
        with(json) {
            val content = string("imageFileContents")
            bytes = if (content != null) Base64.getDecoder().decode(content) else ByteArray(0)
        }
    }
}

class Scenario : JsonModel {
    var simulationDurationMin by property<Double>()
    fun simulationDurationMinProperty() = getProperty(Scenario::simulationDurationMin)

    var simulationStepUs by property<Double>()
    fun simulationStepUsProperty() = getProperty(Scenario::simulationStepUs)

    var movingTargets by property(observableArrayList<MovingTarget>(mutableListOf()))
    fun movingTargetsProperty() = getProperty(Scenario::movingTargets)


    var clutter by property<Clutter>(Clutter())
    fun clutterProperty() = getProperty(Scenario::clutter)


    override fun toJSON(json: JsonBuilder) {
        with(json) {
            add("simulationDurationMin", simulationDurationMin)
            add("simulationStepUs", simulationStepUs)
            add("movingTargets", movingTargets?.toJSON())
            add("clutter", clutter?.toJSON())
        }
    }

    override fun updateModel(json: JsonObject) {
        with(json) {
            simulationDurationMin = double("simulationDurationMin")
            simulationStepUs = double("simulationStepUs")
            movingTargets = getJsonArray("movingTargets")?.toModel()
            clutter = getJsonObject("clutter")?.toModel() ?: Clutter()
        }
    }
}

data class PathSegment(
    val p1: RadarCoordinate,
    val p2: RadarCoordinate,
    val t1Us: Double,
    val t2Us: Double,
    val vxKmUs: Double,
    val vyKmUs: Double,
    val type: MovingTargetType) {

    private val azRad = toRadians(p1.azDeg)
    private val dx = p2.toCartesian().x - p1.toCartesian().x
    private val dy = p2.toCartesian().y - p1.toCartesian().y

    val headingDeg = ((720 + toDegrees(angleToAzimuth(atan2(dy, dx)))) % 360)

    val vKmh = sqrt(pow(vxKmUs, 2.0) + pow(vyKmUs, 2.0)) * HOUR_TO_US

    fun getPositionForTime(timeUs: Double): RadarCoordinate? {
        if (timeUs < t1Us || timeUs > t2Us) {
            return null
        }

        val x1 = p1.rKm * cos(azimuthToAngle(azRad))
        val y1 = p1.rKm * sin(azimuthToAngle(azRad))

        return fromCartesian(
            x1 + (timeUs - t1Us) * vxKmUs,
            y1 + (timeUs - t1Us) * vyKmUs
        )
    }

}

class RadarParameters {
    /**
     * TRIG impulse period - Ti
     */
    var impulsePeriodUs by property<Double>()

    fun impulsePeriodUsProperty() = getProperty(RadarParameters::impulsePeriodUs)

    /**
     * TRIG impulse period - Ti
     */
    var maxImpulsePeriodUs by property<Double>()

    fun maxImpulsePeriodUsProperty() = getProperty(RadarParameters::maxImpulsePeriodUs)


    /**
     * Time between two ARP pulses - Tpr
     */
    var seekTimeSec by property<Double>()

    fun seekTimeSProperty() = getProperty(RadarParameters::seekTimeSec)


    /**
     * Azimuth change pulse - ACP
     */
    var azimuthChangePulse by property<Int>()

    fun azimuthChangePulseProperty() = getProperty(RadarParameters::azimuthChangePulse)

    /**
     * Angle horizontally beam width (degrees) DAZ
     */
    var horizontalAngleBeamWidthDeg by property<Double>()

    fun horizontalAngleBeamWidthDegProperty() = getProperty(RadarParameters::horizontalAngleBeamWidthDeg)

    /**
     * Distance resolution in meters - DRc
     * Depends on the impulse time 1us = 150m
     */
    var distanceResolutionKm by property<Double>()

    fun distanceResolutionKmProperty() = getProperty(RadarParameters::distanceResolutionKm)

    /**
     * Maximum radar distance - Rcmax
     */
    var maxRadarDistanceKm by property<Double>()

    fun maxRadarDistanceKmProperty() = getProperty(RadarParameters::maxRadarDistanceKm)

    /**
     * Minimum radar distance - Rcmin
     */
    var minRadarDistanceKm by property<Double>()

    fun minRadarDistanceKmProperty() = getProperty(RadarParameters::minRadarDistanceKm)
}

/**
 * Options for display units for distance - kilometers or nautical miles
 */
enum class DistanceUnit {
    Km, NM
}

/**
 * Options for display units for distance - kilometers or nautical miles
 */
enum class CoordinateSystem {
    R_AZ, X_Y
}

enum class AzimuthMarkerType {
    FULL, MIN
}

class DisplayParameters {
    /**
     * Draw distance marker circles around the radar
     */
    var distanceStep by property<Double>()

    fun distanceStepProperty() = getProperty(DisplayParameters::distanceStep)

    /**
     * Current displayed distance unit
     */
    var distanceUnit by property<DistanceUnit>()

    fun distanceUnitProperty() = getProperty(DisplayParameters::distanceUnit)

    fun distanceToKmScale() = if (distanceUnit == DistanceUnit.NM)
        1.852
    else
        1.0

    /**
     * Draw azimuth lines
     */
    var azimuthSteps by property<Int>()

    fun azimuthStepsProperty() = getProperty(DisplayParameters::azimuthSteps)

    /**
     * Type of azimuth display
     */
    var azimuthMarkerType by property<AzimuthMarkerType>()

    fun azimuthMarkerTypeProperty() = getProperty(DisplayParameters::azimuthMarkerType)

    /**
     * Current coordinate display system
     */
    var coordinateSystem by property<CoordinateSystem>()

    fun coordinateSystemProperty() = getProperty(DisplayParameters::coordinateSystem)

    /**
     * Current time in the simulation
     */
    var simulatedCurrentTimeSec by property<Double>()

    fun simulatedCurrentTimeSecProperty() = getProperty(DisplayParameters::simulatedCurrentTimeSec)


    var viewPort by property<Bounds?>()
    fun viewPortProperty() = getProperty(DisplayParameters::viewPort)

    /**
     * List of targets to display.
     * If empty then all targets are displayed
     */
    var targetDisplayFilter by property(observableArrayList<String>())

    fun targetDisplayFilterProperty() = getProperty(DisplayParameters::targetDisplayFilter)
}
