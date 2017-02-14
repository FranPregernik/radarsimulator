package hr.franp.rsim

import hr.franp.rsim.models.*
import tornadofx.*

class DesignerController : Controller() {
    val radarParameters: RadarParameters

    val displayParameters: DisplayParameters

    val scenario: Scenario

    /**
     * Selected moving target name
     */
    var selectedMovingTarget by property<MovingTarget>(null)
    val selectedMovingTargetProperty = getProperty(DesignerController::selectedMovingTarget)

    init {
        radarParameters = RadarParameters().apply {
            impulsePeriodUs = 3000.0
            seekTimeSec = 12.0
            azimuthChangePulse = 4096
            horizontalAngleBeamWidthDeg = 1.4
            distanceResolutionKm = 0.150
            maxRadarDistanceKm = 400.0
            minRadarDistanceKm = 5.0
        }

        displayParameters = DisplayParameters().apply {
            distanceStepKm = 50.0
            distanceUnit = DistanceUnit.Km
            azimuthSteps = 36
            azimuthMarkerType = AzimuthMarkerType.FULL
            coordinateSystem = CoordinateSystem.R_AZ
            simulatedCurrentTimeSec = 0.0
        }

        scenario = Scenario().apply {
            simulationDurationMin = 120.0
            simulationStepUs = 10.0
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

    }

}
