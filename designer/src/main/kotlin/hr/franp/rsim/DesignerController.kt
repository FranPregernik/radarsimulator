package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.scene.image.*
import tornadofx.*
import java.lang.Math.*
import java.util.stream.*

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

    /**
     * @return A stream of pairs(acp, us)
     */
    fun calculateTargetHits(fromTimeSec: Double = 0.0,
                            toTimeSec: Double = scenario.simulationDurationMin * MIN_TO_S): Stream<Pair<Int, Int>> {

        val radarParameters = simulationController.radarParameters
        val cParams = CalculationParameters(radarParameters)

        // (deep)clone for background processing
        val scenarioClone = scenario.copy<Scenario>()

        val targetPathSegments = scenarioClone.getAllPathSegments()

        // iterate over full ARP rotations
        return DoubleStream.iterate(fromTimeSec) { it + radarParameters.seekTimeSec }
            .limit(ceil(scenarioClone.simulationDurationMin * MIN_TO_S / radarParameters.seekTimeSec).toLong())
            .filter { it < scenarioClone.simulationDurationMin * MIN_TO_S }
            .boxed()
            .flatMap { minTimeSec ->

                val minTimeUs = S_TO_US * minTimeSec
                val maxTimeUs = S_TO_US * min(
                    max(minTimeSec, toTimeSec),
                    minTimeSec + radarParameters.seekTimeSec
                )

                // and all target paths
                targetPathSegments.stream()
                    .flatMap { pathSegment ->

                        when (pathSegment.type) {
                            MovingTargetType.Point -> calculatePointTargetHits(
                                pathSegment,
                                minTimeUs,
                                maxTimeUs,
                                cParams
                            )
                            MovingTargetType.Cloud1 -> calculateCloudTargetHits(
                                pathSegment,
                                minTimeUs,
                                maxTimeUs,
                                cloudOneImage.getRasterHitMap(),
                                cParams
                            )
                            MovingTargetType.Cloud2 -> calculateCloudTargetHits(
                                pathSegment,
                                minTimeUs,
                                maxTimeUs,
                                cloudTwoImage.getRasterHitMap(),
                                cParams
                            )
                            MovingTargetType.Test1 -> calculateTest1TargetHits(
                                pathSegment,
                                minTimeUs,
                                maxTimeUs,
                                cParams
                            )
                            MovingTargetType.Test2 -> calculateTest2TargetHits(
                                pathSegment,
                                minTimeUs,
                                maxTimeUs,
                                cParams
                            )
                        }
                    }
            }
    }


    fun calculateClutterHits(): Stream<Pair<Int, Int>> {

        val radarParameters = simulationController.radarParameters
        val cParams = CalculationParameters(radarParameters)

        // (deep)clone for background processing
        val scenario = this.scenario.copy<Scenario>()

        val maxRadarDistanceKm = radarParameters.maxRadarDistanceKm
        val distanceResolutionKm = radarParameters.distanceResolutionKm

        val width = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()
        val height = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()

        val raster = scenario.clutter?.getImage(width, height)?.getRasterHitMap() ?: return Stream.empty()

        return calculateClutterHits(raster, cParams)
    }


}
