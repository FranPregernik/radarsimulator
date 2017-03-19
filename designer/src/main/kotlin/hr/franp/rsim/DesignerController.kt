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

    fun calculateTargetHits(fromTimeSec: Double = 0.0,
                            toTimeSec: Double = scenario.simulationDurationMin * MIN_TO_S): Stream<Bits> {

        val radarParameters = simulationController.radarParameters
        val cParams = CalculationParameters(radarParameters)

        // (deep)clone for background processing
        val scenarioClone = scenario.copy<Scenario>()

        val targetPathSegments = scenarioClone.getAllPathSegments()

        val arpTimeIterator = generateSequence(fromTimeSec) { it + radarParameters.seekTimeSec }
            .takeWhile { it < toTimeSec }
            .iterator()

        // iterate over full ARP rotations
        return stream(spliteratorUnknownSize(arpTimeIterator, Spliterator.ORDERED), false)
            .map { minTimeSec ->

                val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())

                val minTimeUs = S_TO_US * minTimeSec
                val maxTimeUs = S_TO_US * min(
                    toTimeSec,
                    minTimeSec + radarParameters.seekTimeSec
                )

                targetPathSegments.forEach tps@ { pathSegment ->

                    when (pathSegment.type) {
                        MovingTargetType.Point -> calculatePointTargetHits(
                            hits,
                            pathSegment,
                            minTimeUs,
                            maxTimeUs,
                            cParams
                        )
                        MovingTargetType.Cloud1 -> calculateCloudTargetHits(
                            hits,
                            pathSegment,
                            minTimeUs,
                            maxTimeUs,
                            cloudOneImage.getRasterHitMap(),
                            cParams
                        )
                        MovingTargetType.Cloud2 -> calculateCloudTargetHits(
                            hits,
                            pathSegment,
                            minTimeUs,
                            maxTimeUs,
                            cloudTwoImage.getRasterHitMap(),
                            cParams
                        )
                        MovingTargetType.Test1 -> calculateTest1TargetHits(
                            hits,
                            pathSegment,
                            minTimeUs,
                            maxTimeUs,
                            cParams
                        )
                        MovingTargetType.Test2 -> calculateTest2TargetHits(
                            hits,
                            pathSegment,
                            minTimeUs,
                            maxTimeUs,
                            cParams
                        )
                    }
                }

                hits
            }
    }


    fun calculateClutterHits(): Stream<Bits> {

        val radarParameters = simulationController.radarParameters
        val cParams = CalculationParameters(radarParameters)

        // (deep)clone for background processing
        val scenario = this.scenario.copy<Scenario>()

        val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())

        val maxRadarDistanceKm = radarParameters.maxRadarDistanceKm
        val distanceResolutionKm = radarParameters.distanceResolutionKm

        val width = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()
        val height = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()

        val raster = scenario.clutter?.getImage(width, height)?.getRasterHitMap() ?: return Stream.of(hits)

        calculateClutterHits(hits, raster, cParams)

        return Stream.of(hits)
    }


}
