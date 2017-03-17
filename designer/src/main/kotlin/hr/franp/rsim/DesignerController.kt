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

        val targetPathSegments = scenarioClone.getAllPathSegments()
        val simulationDurationSec = scenarioClone.simulationDurationMin * MIN_TO_S

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
