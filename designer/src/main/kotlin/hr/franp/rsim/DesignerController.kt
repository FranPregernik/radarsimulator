package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.scene.image.*
import tornadofx.*
import java.lang.Math.*
import java.nio.*
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
    fun calculateTargetHits(buff: ByteBuffer,
                            spread: Boolean = false,
                            compress: Boolean = false,
                            fromTimeSec: Double = 0.0,
                            toTimeSec: Double = scenario.simulationDurationMin * MIN_TO_S) {

        val radarParameters = simulationController.radarParameters
        val cParams = CalculationParameters(radarParameters)

        if (scenario.movingTargets.isEmpty()) {
            return
        }
        val simulationDurationMin = scenario.simulationDurationMin
        val targetPathSegments = scenario.getAllPathSegments()

        // iterate over time
        DoubleStream.iterate(fromTimeSec) { it + radarParameters.seekTimeSec }
            .limit(ceil(simulationDurationMin * MIN_TO_S / radarParameters.seekTimeSec).toLong())
            .filter { it < simulationDurationMin * MIN_TO_S }
            .boxed()
            .forEach { minTimeSec ->

                val minTimeUs = S_TO_US * minTimeSec
                val maxTimeUs = S_TO_US * min(
                    max(minTimeSec, toTimeSec),
                    minTimeSec + radarParameters.seekTimeSec
                )

                // and all target paths
                targetPathSegments.forEach { pathSegment ->
                    when (pathSegment.type) {
                        MovingTargetType.Point -> buff.calculatePointTargetHits(
                            pathSegment,
                            minTimeUs,
                            maxTimeUs,
                            cParams,
                            spread,
                            compress
                        )
                        MovingTargetType.Test1 -> buff.calculateTest1TargetHits(
                            pathSegment,
                            minTimeUs,
                            maxTimeUs,
                            cParams,
                            spread,
                            compress
                        )
                        MovingTargetType.Test2 -> buff.calculateTest2TargetHits(
                            pathSegment,
                            minTimeUs,
                            maxTimeUs,
                            cParams,
                            spread,
                            compress
                        )
                        else -> {
                            // no op
                            // clouds are not calculated as moving targets
                        }
                    }
                }
            }
    }


    fun calculateClutterHits(buff: ByteBuffer, spread: Boolean) {

        val radarParameters = simulationController.radarParameters
        val cParams = CalculationParameters(radarParameters)

        // (deep)clone for background processing
        val scenario = this.scenario.copy<Scenario>()

        val maxRadarDistanceKm = radarParameters.maxRadarDistanceKm
        val distanceResolutionKm = radarParameters.distanceResolutionKm

        val width = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()
        val height = Math.round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()

        val raster = scenario.clutter?.getImage(width, height)?.getRasterHitMap() ?: return

        buff.calculateClutterMapHits(raster, cParams, spread)

        // HACK: we are using moving targets as fixed point clutter maps
        // TODO: refactor into own scenario.clutterMaps
        scenario.movingTargets
            .filter { it.type == MovingTargetType.Cloud1 || it.type == MovingTargetType.Cloud2 }
            .forEach {
                when (it.type) {
                    MovingTargetType.Cloud1 -> buff.calculateClutterMapHits(
                        cloudOneImage.getRasterHitMap(),
                        cParams,
                        spread,
                        it.initialPosition,
                        1.0
                    )
                    MovingTargetType.Cloud2 -> buff.calculateClutterMapHits(
                        cloudTwoImage.getRasterHitMap(),
                        cParams,
                        spread,
                        it.initialPosition,
                        1.0
                    )
                    else -> {
                        // no op
                        // no other moving target is clutter
                    }
                }
            }
    }


}
