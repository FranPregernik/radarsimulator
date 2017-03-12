package hr.franp.rsim

import hr.franp.*
import hr.franp.rsim.models.*
import javafx.scene.image.*
import tornadofx.*
import java.lang.Math.*
import java.util.*
import java.util.Spliterators.*
import java.util.stream.*
import java.util.stream.StreamSupport.*

class DesignerController : Controller() {

    val cloudOneImage = processHitMaskImage(Image(resources["/cloud1.png"]))
    val cloudTwoImage = processHitMaskImage(Image(resources["/cloud2.png"]))

    private val simulationController: SimulatorController by inject()

    var scenario by property(Scenario().apply {
        simulationDurationMin = 120.0
        simulationStepUs = 100000.0
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
    })

    val scenarioProperty = getProperty(DesignerController::scenario)

    /**
     * Selected moving target name
     */
    var selectedMovingTarget by property<MovingTarget>(null)
    val selectedMovingTargetProperty = getProperty(DesignerController::selectedMovingTarget)

    fun calculateTargetHits(): Stream<Bits> {

        val radarParameters = simulationController.radarParameters
        val cParams = CalculationParameters(radarParameters)

        // (deep)clone for background processing
        val scenarioClone = scenario.copy<Scenario>()

        val movingTargets = scenarioClone.movingTargets ?: emptyList<MovingTarget>()
        val targetPathSegments = movingTargets
            .flatMap { movingTarget ->
                var p1 = movingTarget.initialPosition
                var t1 = 0.0

                if (movingTarget.directions?.size == 0) {
                    // hovering or standing still
                    listOf(PathSegment(
                        p1 = p1,
                        p2 = p1,
                        t1Us = t1,
                        t2Us = scenarioClone.simulationDurationMin * MIN_TO_US,
                        vxKmUs = 0.0,
                        vyKmUs = 0.0,
                        type = movingTarget.type
                    ))
                } else {
                    // moving targets
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
            }

        val simulationDurationSec = scenarioClone.simulationDurationMin * 60.0

//        // iterate over full ARP rotations
//        targetPathSegments.stream()
//            .flatMap { ps ->
//
//                val stepTime = 0.5 * min(
//                    radarParameters.distanceResolutionKm / ps.vxKmUs,
//                    radarParameters.distanceResolutionKm / ps.vyKmUs
//                )
//                val simTimeIterator = generateSequence(ps.t1Us) { it + stepTime }
//                    .takeWhile { it < ps.t2Us && it < simulationDurationSec }
//                    .iterator()
//
//                // iterate in one ARP rotation every simulation step time period
//                stream(spliteratorUnknownSize(simTimeIterator, Spliterator.ORDERED), false)
//                    .map { Pair(it, ps) }
//
//            }
//            // group by ARP
////            .parallel()
//            .forEach { pair ->
//
//                val timeUs = pair.first
//                val pathSegment = pair.second
//                val position = pathSegment.getPositionForTime(timeUs) ?: return@forEach
//
//                val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
//
//                when (pathSegment.type) {
//                    MovingTargetType.Point ->
//                        calculatePointTargetHits(hits, position, timeUs, cParams)
//                    MovingTargetType.Cloud1 ->
//                        calculateCloudTargetHits(hits, position, cloudOneImage.getRasterHitMap(), cParams)
//                    MovingTargetType.Cloud2 ->
//                        calculateCloudTargetHits(hits, position, cloudTwoImage.getRasterHitMap(), cParams)
//                    MovingTargetType.Test1 ->
//                        calculateTestTargetHits(hits, position, timeUs, cParams)
//                    MovingTargetType.Test2 ->
//                        calculateTestTargetHits(hits, position, timeUs, cParams)
//                }
//
//            }

        val arpTimeIterator = generateSequence(0.0) { it + radarParameters.seekTimeSec }
            .takeWhile { it < simulationDurationSec }
            .iterator()

        // iterate over full ARP rotations
        return stream(spliteratorUnknownSize(arpTimeIterator, Spliterator.ORDERED), false)
            .map { minTimeSec ->

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

                            // for Test1 the time is discrete and rounded down to seekTimeSec
                            val rotationTime: Double
                            if (pathSegment.type == MovingTargetType.Test1) {
                                rotationTime = floor(tUs / cParams.rotationTimeUs) * cParams.rotationTimeUs
                            } else {
                                rotationTime = tUs
                            }

                            val plotPos = pathSegment.getPositionForTime(rotationTime) ?: return@tps
                            val sweepHeadingRad = TWO_PI / cParams.rotationTimeUs * tUs

                            when (pathSegment.type) {
                                MovingTargetType.Point -> calculatePointTargetHits(
                                    hits,
                                    plotPos,
                                    sweepHeadingRad,
                                    cParams
                                )
                                MovingTargetType.Cloud1 -> calculateCloudTargetHits(
                                    hits,
                                    plotPos,
                                    cloudOneImage.getRasterHitMap(),
                                    cParams
                                )
                                MovingTargetType.Cloud2 -> calculateCloudTargetHits(
                                    hits,
                                    plotPos,
                                    cloudTwoImage.getRasterHitMap(),
                                    cParams
                                )
                                MovingTargetType.Test1 -> calculateTest1TargetHits(
                                    hits,
                                    plotPos,
                                    sweepHeadingRad,
                                    cParams
                                )
                                MovingTargetType.Test2 -> calculateTest2TargetHits(
                                    hits,
                                    plotPos,
                                    sweepHeadingRad,
                                    cParams
                                )
                            }
                        }
                    }

                hits
            }

    }


    fun calculateClutterHits(): Bits {

        val radarParameters = simulationController.radarParameters
        val cParams = CalculationParameters(radarParameters)

        // (deep)clone for background processing
        val scenario = this.scenario.copy<Scenario>()

        val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())

        val maxRadarDistanceKm = radarParameters.maxRadarDistanceKm
        val distanceResolutionKm = radarParameters.distanceResolutionKm

        val width = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()
        val height = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()

        val raster = scenario.clutter?.getImage(width, height)?.getRasterHitMap() ?: return hits

        calculateClutterHits(hits, raster, cParams)

        return hits
    }


}
