@file:Suppress("unused")

package hr.franp.rsim

import hr.franp.*
import hr.franp.rsim.models.*
import javafx.geometry.*
import javafx.scene.image.*
import javafx.scene.paint.*
import org.amshove.kluent.*
import org.jetbrains.spek.api.*
import org.jetbrains.spek.api.dsl.*
import java.lang.Math.*

class HelpersTest : Spek({

    val radarParameters = RadarParameters(
        impulsePeriodUs = 3003.0,
        maxImpulsePeriodUs = 3072.0,
        seekTimeSec = 12.0,
        azimuthChangePulse = 4096,
        horizontalAngleBeamWidthDeg = 1.4,
        distanceResolutionKm = 0.150,
        maxRadarDistanceKm = 400.0,
        minRadarDistanceKm = 5.0
    )
    val cParams = CalculationParameters(radarParameters)

    given("A point target in distance detection range") {

        val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
        val position = RadarCoordinate(100.0, 10.0)

        beforeEachTest { hits.clear() }


        on("calculating the hit for sweep angle just prior heading range") {

            val sweepHeadingRad = toRadians(position.azDeg - radarParameters.horizontalAngleBeamWidthDeg)

            calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)

            it("should result in no detection") {
                hits.nextSetBit(0) shouldEqual -1
            }
        }

        on("calculating the hit for sweep angle just after heading range") {

            val sweepHeadingRad = toRadians(position.azDeg + radarParameters.horizontalAngleBeamWidthDeg)

            calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)

            it("should result in no detection") {
                hits.nextSetBit(0) shouldEqual -1
            }
        }

        on("calculating the hit for sweep angle inside heading range") {

            val sweepHeadingRad = toRadians(position.azDeg)

            calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)

            it("should result in detection") {
                hits.nextSetBit(0) shouldNotEqual -1
            }
        }
    }

    given("A point target outside distance detection range") {

        val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
        val azDeg = 10.0
        val sweepHeadingRad = toRadians(azDeg)

        beforeEachTest { hits.clear() }

        on("calculating the close target hit") {

            val position = RadarCoordinate(radarParameters.minRadarDistanceKm - 1, azDeg)

            calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)

            it("should result in no detection") {
                hits.nextSetBit(0) shouldEqual -1
            }
        }

        on("calculating the far target hit") {

            val position = RadarCoordinate(radarParameters.maxRadarDistanceKm + 1, azDeg)

            calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)

            it("should result in no detection") {
                hits.nextSetBit(0) shouldEqual -1
            }
        }

    }

    given("Targets in all quadrants") {

        on("calculating the 1st quadrant") {

            val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
            val position = RadarCoordinate(100.0, 45.0)

            (0..(radarParameters.seekTimeSec * S_TO_US).toInt())
                .map { tUs -> TWO_PI / cParams.rotationTimeUs * tUs }
                .forEach { sweepHeadingRad ->
                    calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)
                }

            it("should result in detection") {
                hits.nextSetBit(0) shouldNotEqual -1
            }
        }

        on("calculating the 2nd quadrant") {

            val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
            val position = RadarCoordinate(100.0, 135.0)

            (0..(radarParameters.seekTimeSec * S_TO_US).toInt())
                .map { tUs -> TWO_PI / cParams.rotationTimeUs * tUs }
                .forEach { sweepHeadingRad ->
                    calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)
                }

            it("should result in detection") {
                hits.nextSetBit(0) shouldNotEqual -1
            }
        }

        on("calculating the 3rd quadrant") {

            val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
            val position = RadarCoordinate(100.0, 225.0)

            (0..(radarParameters.seekTimeSec * S_TO_US).toInt())
                .map { tUs -> TWO_PI / cParams.rotationTimeUs * tUs }
                .forEach { sweepHeadingRad ->
                    calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)
                }

            it("should result in detection") {
                hits.nextSetBit(0) shouldNotEqual -1
            }
        }

        on("calculating the 4th quadrant") {

            val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
            val position = RadarCoordinate(100.0, 315.0)

            (0..(radarParameters.seekTimeSec * S_TO_US).toInt())
                .map { tUs -> TWO_PI / cParams.rotationTimeUs * tUs }
                .forEach { sweepHeadingRad ->
                    calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)
                }

            it("should result in detection") {
                hits.nextSetBit(0) shouldNotEqual -1
            }
        }
    }

    given("A clutter map in detection range with hdg in [270, 90]") {

        val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
        val width = round(2.0 * radarParameters.maxRadarDistanceKm / radarParameters.distanceResolutionKm).toInt()
        val height = round(2.0 * radarParameters.maxRadarDistanceKm / radarParameters.distanceResolutionKm).toInt()
        val clutterMap = WritableImage(width, height)
        clutterMap.pixelWriter.setColor(width / 4, height / 4, Color.WHITE)

        on("calculating the hit") {

            calculateClutterHits(hits, RasterIterator(clutterMap), cParams)

            it("should result in  detection") {
                hits.nextSetBit(0) shouldNotEqual -1
            }
        }
    }


    given("A zoomed in cartesian region in quadrant 1") {

        val region = BoundingBox(100.0, 100.0, 300.0, 400.0)
        val window = BoundingBox(0.0, 0.0, 600.0, 800.0)

        val affine = setupViewPort(region, window)

        on("Calculating the lower left point of region") {
            val transPoint = affine.transform(region.minX, region.minY)

            it("should transform to lower left point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.minX, window.maxY)
            }
        }

        on("Calculating the upper left point of region") {
            val transPoint = affine.transform(region.minX, region.maxY)

            it("should transform to upper left point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.minX, window.minY)
            }
        }

        on("Calculating the lower right point of region") {
            val transPoint = affine.transform(region.maxX, region.minY)

            it("should transform to lower right point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.maxX, window.maxY)
            }
        }

        on("Calculating the upper right point of region") {
            val transPoint = affine.transform(region.maxX, region.maxY)

            it("should transform to upper right point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.maxX, window.minY)
            }
        }

    }

    given("A zoomed in cartesian region in quadrant 2") {

        val region = BoundingBox(-400.0, 100.0, 300.0, 400.0)
        val window = BoundingBox(0.0, 0.0, 600.0, 800.0)

        val affine = setupViewPort(region, window)

        on("Calculating the lower left point of region") {
            val transPoint = affine.transform(region.minX, region.minY)

            it("should transform to lower left point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.minX, window.maxY)
            }
        }

        on("Calculating the upper left point of region") {
            val transPoint = affine.transform(region.minX, region.maxY)

            it("should transform to upper left point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.minX, window.minY)
            }
        }

        on("Calculating the lower right point of region") {
            val transPoint = affine.transform(region.maxX, region.minY)

            it("should transform to lower right point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.maxX, window.maxY)
            }
        }

        on("Calculating the upper right point of region") {
            val transPoint = affine.transform(region.maxX, region.maxY)

            it("should transform to upper right point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.maxX, window.minY)
            }
        }

    }

    given("A zoomed in cartesian region in quadrant 3") {

        val region = BoundingBox(-400.0, -500.0, 300.0, 400.0)
        val window = BoundingBox(0.0, 0.0, 600.0, 800.0)

        val affine = setupViewPort(region, window)

        on("Calculating the lower left point of region") {
            val transPoint = affine.transform(region.minX, region.minY)

            it("should transform to lower left point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.minX, window.maxY)
            }
        }

        on("Calculating the upper left point of region") {
            val transPoint = affine.transform(region.minX, region.maxY)

            it("should transform to upper left point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.minX, window.minY)
            }
        }

        on("Calculating the lower right point of region") {
            val transPoint = affine.transform(region.maxX, region.minY)

            it("should transform to lower right point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.maxX, window.maxY)
            }
        }

        on("Calculating the upper right point of region") {
            val transPoint = affine.transform(region.maxX, region.maxY)

            it("should transform to upper right point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.maxX, window.minY)
            }
        }

    }

    given("A zoomed in cartesian region in quadrant 4") {

        val region = BoundingBox(100.0, -500.0, 300.0, 400.0)
        val window = BoundingBox(0.0, 0.0, 600.0, 800.0)

        val affine = setupViewPort(region, window)

        on("Calculating the lower left point of region") {
            val transPoint = affine.transform(region.minX, region.minY)

            it("should transform to lower left point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.minX, window.maxY)
            }
        }

        on("Calculating the upper left point of region") {
            val transPoint = affine.transform(region.minX, region.maxY)

            it("should transform to upper left point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.minX, window.minY)
            }
        }

        on("Calculating the lower right point of region") {
            val transPoint = affine.transform(region.maxX, region.minY)

            it("should transform to lower right point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.maxX, window.maxY)
            }
        }

        on("Calculating the upper right point of region") {
            val transPoint = affine.transform(region.maxX, region.maxY)

            it("should transform to upper right point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.maxX, window.minY)
            }
        }

    }

    given("A zoomed in cartesian region in covering all quadrants") {

        val region = BoundingBox(-100.0, -100.0, 300.0, 400.0)
        val window = BoundingBox(0.0, 0.0, 600.0, 800.0)

        val affine = setupViewPort(region, window)

        on("Calculating the lower left point of region") {
            val transPoint = affine.transform(region.minX, region.minY)

            it("should transform to lower left point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.minX, window.maxY)
            }
        }

        on("Calculating the upper left point of region") {
            val transPoint = affine.transform(region.minX, region.maxY)

            it("should transform to upper left point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.minX, window.minY)
            }
        }

        on("Calculating the lower right point of region") {
            val transPoint = affine.transform(region.maxX, region.minY)

            it("should transform to lower right point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.maxX, window.maxY)
            }
        }

        on("Calculating the upper right point of region") {
            val transPoint = affine.transform(region.maxX, region.maxY)

            it("should transform to upper right point of window") {
                // window is in screen coordinate system (inverted y)
                transPoint shouldEqual Point2D(window.maxX, window.minY)
            }
        }

    }
})