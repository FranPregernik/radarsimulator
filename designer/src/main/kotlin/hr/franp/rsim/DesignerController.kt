package hr.franp.rsim

import hr.franp.rsim.models.AzimuthMarkerType
import hr.franp.rsim.models.CoordinateSystem
import hr.franp.rsim.models.Direction
import hr.franp.rsim.models.DisplayParameters
import hr.franp.rsim.models.DistanceUnit
import hr.franp.rsim.models.MovingTarget
import hr.franp.rsim.models.MovingTargetType
import hr.franp.rsim.models.RadarCoordinate
import hr.franp.rsim.models.RadarParameters
import hr.franp.rsim.models.Scenario
import tornadofx.Controller
import tornadofx.getProperty
import tornadofx.observable
import tornadofx.property

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
                        Direction().apply {
                            speedKmh = 1100.0
                            destination = RadarCoordinate.fromCartesian(50.0, 50.0)
                        },
                        Direction().apply {
                            speedKmh = 400.0
                            destination = RadarCoordinate.fromCartesian(300.0, 300.0)
                        },
                        Direction().apply {
                            speedKmh = 400.0
                            destination = RadarCoordinate.fromCartesian(-200.0, 300.0)
                        }
                    ).observable()
                },
                MovingTarget().apply {
                    name = "T2"
                    type = MovingTargetType.Point
                    initialPosition = RadarCoordinate.fromCartesian(-100.0, 10.0)
                    directions = mutableListOf(
                        Direction().apply {
                            speedKmh = 1100.0
                            destination = RadarCoordinate.fromCartesian(55.0, 55.0)
                        },
                        Direction().apply {
                            speedKmh = 400.0
                            destination = RadarCoordinate.fromCartesian(305.0, 305.0)
                        },
                        Direction().apply {
                            speedKmh = 400.0
                            destination = RadarCoordinate.fromCartesian(-205.0, 305.0)
                        }
                    ).observable()
                },
                MovingTarget().apply {
                    name = "T3"
                    type = MovingTargetType.Point
                    initialPosition = RadarCoordinate.fromCartesian(300.0, -150.0)
                    directions = mutableListOf(
                        Direction().apply {
                            speedKmh = 2000.0
                            destination = RadarCoordinate.fromCartesian(-300.0, -120.0)
                        }
                    ).observable()
                }
            ).observable()
        }

    }

}
