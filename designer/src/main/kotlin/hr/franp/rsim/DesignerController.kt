package hr.franp.rsim

import hr.franp.*
import hr.franp.rsim.models.*
import tornadofx.*
import java.util.*
import java.util.Spliterators.*
import java.util.stream.*
import java.util.stream.StreamSupport.*

class DesignerController : Controller() {
    val radarParameters: RadarParameters = RadarParameters().apply {
        impulsePeriodUs = 3003.0
        maxImpulsePeriodUs = 3072.0
        seekTimeSec = 12.0
        azimuthChangePulse = 4096
        horizontalAngleBeamWidthDeg = 1.4
        distanceResolutionKm = 0.150
        maxRadarDistanceKm = 400.0
        minRadarDistanceKm = 5.0
    }

    val displayParameters: DisplayParameters = DisplayParameters().apply {
        distanceStepKm = 50.0
        distanceUnit = DistanceUnit.Km
        azimuthSteps = 36
        azimuthMarkerType = AzimuthMarkerType.FULL
        coordinateSystem = CoordinateSystem.R_AZ
        simulatedCurrentTimeSec = 0.0
    }

    val scenario: Scenario = Scenario().apply {
        simulationDurationMin = 120.0
        simulationStepUs = 1000.0
        movingTargets = mutableListOf(
            MovingTarget().apply {
                name = "T1"
                type = MovingTargetType.Point
                initialPosition = RadarCoordinate.fromCartesian(10.0, -100.0)
                directions = mutableListOf(
                    Direction(
                        speedKmh = 750.0,
                        destination = RadarCoordinate.fromCartesian(50.0, 50.0)
                    ),
                    Direction(
                        speedKmh = 750.0,
                        destination = RadarCoordinate.fromCartesian(300.0, 300.0)
                    ),
                    Direction(
                        speedKmh = 750.0,
                        destination = RadarCoordinate.fromCartesian(-200.0, 300.0)
                    )
                ).observable()
            },
            MovingTarget().apply {
                name = "T2"
                type = MovingTargetType.Point
                initialPosition = RadarCoordinate.fromCartesian(-100.0, 10.0)
                directions = mutableListOf(
                    Direction(
                        speedKmh = 1200.0,
                        destination = RadarCoordinate.fromCartesian(55.0, 55.0)
                    ),
                    Direction(
                        speedKmh = 1200.0,
                        destination = RadarCoordinate.fromCartesian(305.0, 305.0)
                    ),
                    Direction(
                        speedKmh = 1200.0,
                        destination = RadarCoordinate.fromCartesian(-205.0, 305.0)
                    )
                ).observable()
            },
            MovingTarget().apply {
                name = "T3"
                type = MovingTargetType.Point
                initialPosition = RadarCoordinate.fromCartesian(400.0, -350.0)
                directions = mutableListOf(
                    Direction(
                        speedKmh = 900.0,
                        destination = RadarCoordinate.fromCartesian(-400.0, -320.0)
                    ),
                    Direction(
                        speedKmh = 900.0,
                        destination = RadarCoordinate.fromCartesian(-320.0, -100.0)
                    )
                ).observable()
            }
        ).observable()
    }

    /**
     * Selected moving target name
     */
    var selectedMovingTarget by property<MovingTarget>(null)
    val selectedMovingTargetProperty = getProperty(DesignerController::selectedMovingTarget)

    fun calculateTargetHits(): Stream<Bits> {

        // (deep)clone for background processing
        val scenarioClone = scenario.copy<Scenario>()
        val radarParameters = radarParameters

        val movingTargets = scenarioClone.movingTargets ?: emptyList<MovingTarget>()
        val targetPathSegments = movingTargets
            .flatMap { movingTarget ->
                var p1 = movingTarget.initialPosition
                var t1 = 0.0

                movingTarget.directions.map { direction ->
                    val p2 = direction.destination
                    val speedKmUs = direction.speedKmh / HOUR_TO_US

                    // distance from last course change point
                    val p1c = p1.toCartesian()
                    val p2c = p2.toCartesian()
                    val dx = p2c.x - p1c.x
                    val dy = p2c.y - p1c.y
                    val distance = Math.sqrt(Math.pow(dx, 2.0) + Math.pow(dy, 2.0))
                    val dt = distance / speedKmUs


                    val pathSegment = PathSegment(
                        p1 = p1,
                        p2 = p2,
                        t1Us = t1,
                        t2Us = t1 + dt,
                        vxKmUs = speedKmUs * dx / distance,
                        vyKmUs = speedKmUs * dy / distance,
                        type = movingTarget.type
                    )

                    p1 = p2
                    t1 += dt

                    pathSegment
                }
            }

        val simulationDurationSec = scenarioClone.simulationDurationMin * 60.0

        val arpTimeIterator = generateSequence(0.0) { it + radarParameters.seekTimeSec }
            .takeWhile { it < simulationDurationSec }
            .iterator()

        // iterate over full ARP rotations
        return stream(spliteratorUnknownSize(arpTimeIterator, Spliterator.ORDERED), false)
            .map { minTimeSec ->

                println("$minTimeSec/$simulationDurationSec")

                val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())

                val minTimeUs = minTimeSec * S_TO_US
                val maxTimeUs = (minTimeSec + radarParameters.seekTimeSec) * S_TO_US

                val simTimeIterator = generateSequence(minTimeUs) { it + scenarioClone.simulationStepUs }
                    .takeWhile { it < maxTimeUs }
                    .iterator()

                // iterate in one ARP rotation every simulation step time period
                stream(spliteratorUnknownSize(simTimeIterator, Spliterator.ORDERED), false)
                    .forEach { tUs ->
                        targetPathSegments.forEach tps@ { pathSegment ->
                            val position = pathSegment.getPositionForTime(tUs) ?: return@tps

                            when (pathSegment.type) {
                                MovingTargetType.Point ->
                                    calculatePointTargetHits(hits, position, tUs, radarParameters)
                                MovingTargetType.Cloud1 ->
                                    calculateCloudTargetHits(hits, position, tUs, radarParameters)
                                MovingTargetType.Cloud2 ->
                                    calculateCloudTargetHits(hits, position, tUs, radarParameters)
                                MovingTargetType.Test1 ->
                                    calculateTestTargetHits(hits, position, tUs, radarParameters)
                                MovingTargetType.Test2 ->
                                    calculateTestTargetHits(hits, position, tUs, radarParameters)
                            }
                        }
                    }

                hits
            }

    }


    fun calculateClutterHits(): Bits {
        // (deep)clone for background processing
        val scenario = this.scenario.copy<Scenario>()

        val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())

        val maxRadarDistanceKm = radarParameters.maxRadarDistanceKm
        val distanceResolutionKm = radarParameters.distanceResolutionKm

        val width = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()
        val height = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()

        val raster = scenario.clutter?.getRasterHitMap(width, height) ?: return hits

        calculateClutterHits(hits, raster, radarParameters)

        return hits
    }


}
