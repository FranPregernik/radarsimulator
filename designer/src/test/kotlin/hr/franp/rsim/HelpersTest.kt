@file:Suppress("unused")

package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.geometry.*
import org.amshove.kluent.*
import org.jetbrains.spek.api.*
import org.jetbrains.spek.api.dsl.*
import java.nio.*
import java.util.stream.*


class HelpersTest : Spek({

    val radarParameters = RadarParameters(
        impulsePeriodUs = 3003.0,
        maxImpulsePeriodUs = 3072.0,
        impulseSignalUs = 3.0,
        seekTimeSec = 12.0,
        azimuthChangePulse = 4096,
        horizontalAngleBeamWidthDeg = 1.4,
        distanceResolutionKm = 0.150,
        maxRadarDistanceKm = 400.0,
        minRadarDistanceKm = 5.0
    )
    val cParams = CalculationParameters(radarParameters)

    given("an empty stream") {

        on("writing the simulation file") {

            val buffer = ByteBuffer.allocate(FILE_HEADER_BYTE_CNT)
                .order(ByteOrder.LITTLE_ENDIAN)

            buffer.writeHitsHeader(radarParameters, 100)

            it("should have a correct header") {

                buffer.getInt(0) shouldEqual (radarParameters.seekTimeSec * S_TO_US).toInt()
                buffer.getInt(4) shouldEqual radarParameters.azimuthChangePulse
                buffer.getInt(8) shouldEqual radarParameters.impulsePeriodUs.toInt()
                buffer.getInt(12) shouldEqual radarParameters.maxImpulsePeriodUs.toInt()
                buffer.getInt(16) shouldEqual 100
            }

        }
    }

    given("a non empty hit stream") {

        val hitStream = Stream.of<Pair<Int, Int>>(
            Pair(0, 0),
            Pair(0, radarParameters.impulsePeriodUs.toInt()),
            Pair(radarParameters.azimuthChangePulse, 0),
            Pair(radarParameters.azimuthChangePulse, radarParameters.impulsePeriodUs.toInt())
        )

        val buffer = ByteBuffer.allocate(cParams.arpByteCnt.toInt())
            .order(ByteOrder.LITTLE_ENDIAN)

        on("writing the simulation file") {

            hitStream.forEach { pair ->
                buffer.writeHit(pair.first, pair.second, cParams)
            }

            it("should have initial ACP 0 hits") {
                buffer[0] shouldNotBe 0b0
                buffer[radarParameters.impulsePeriodUs.toInt() / 8] shouldNotBe 0b0
            }

            it("should have the last ACP hits") {
                val pos = (cParams.acpByteCnt * cParams.azimuthChangePulseCount).toInt()

                buffer[pos] shouldNotBe 0b0
                buffer[pos + radarParameters.impulsePeriodUs.toInt() / 8] shouldNotBe 0b0
            }

        }
    }

    given("a point target in distance detection range") {

        val position = RadarCoordinate(100.0, 10.0)
        val pathSegment = PathSegment(
            p1 = position,
            p2 = position,
            t1Us = 0.0,
            t2Us = radarParameters.seekTimeSec * S_TO_US,
            vxKmUs = 0.0,
            vyKmUs = 0.0,
            type = MovingTargetType.Point
        )

        on("calculating the hit for sweep angle just prior heading range") {

            val sweepHeadingDeg = position.azDeg - radarParameters.horizontalAngleBeamWidthDeg
            val tUs = sweepHeadingDeg / 360.0 * cParams.rotationTimeUs
            val hits = buff.calculatePointTargetHits(pathSegment, tUs, tUs, cParams, false).count()

            it("should result in no detection") {
                hits shouldEqualTo 0
            }
        }

        on("calculating the hit for sweep angle just after heading range") {

            val sweepHeadingDeg = position.azDeg + radarParameters.horizontalAngleBeamWidthDeg
            val tUs = sweepHeadingDeg / 360.0 * cParams.rotationTimeUs
            val hits = buff.calculatePointTargetHits(pathSegment, tUs, tUs, cParams, false).count()

            it("should result in no detection") {
                hits shouldEqualTo 0
            }
        }

        on("calculating the hit for sweep angle inside heading range") {

            val sweepHeadingDeg = position.azDeg
            val tUs = sweepHeadingDeg / 360.0 * cParams.rotationTimeUs
            val hits = buff.calculatePointTargetHits(pathSegment, tUs - cParams.rotationTimeUs, tUs + cParams.rotationTimeUs, cParams, false)
                .collect(Collectors.toList())

            it("should result in detection") {
                hits.size shouldEqualTo 1
                hits[0].first shouldEqualTo (position.azDeg / 360.0 * cParams.azimuthChangePulseCount).toInt()
                hits[0].second shouldEqualTo (position.rKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM).toInt()
            }
        }
    }

//    given("a point target outside distance detection range") {
//
//        val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
//        val azDeg = 10.0
//        val sweepHeadingRad = toRadians(azDeg)
//
//        beforeEachTest { hits.clear() }
//
//        on("calculating the close target hit") {
//
//            val position = RadarCoordinate(radarParameters.minRadarDistanceKm - 1, azDeg)
//
//            calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)
//
//            it("should result in no detection") {
//                hits.nextSetBit(0) shouldEqual -1
//            }
//        }
//
//        on("calculating the far target hit") {
//
//            val position = RadarCoordinate(radarParameters.maxRadarDistanceKm + 1, azDeg)
//
//            calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)
//
//            it("should result in no detection") {
//                hits.nextSetBit(0) shouldEqual -1
//            }
//        }
//
//    }
//
//    given("Targets in all quadrants") {
//
//        on("calculating the 1st quadrant") {
//
//            val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
//            val position = RadarCoordinate(100.0, 45.0)
//
//            (0..(radarParameters.seekTimeSec * S_TO_US).toInt())
//                .map { tUs -> TWO_PI / cParams.rotationTimeUs * tUs }
//                .forEach { sweepHeadingRad ->
//                    calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)
//                }
//
//            it("should result in detection") {
//                hits.nextSetBit(0) shouldNotEqual -1
//            }
//        }
//
//        on("calculating the 2nd quadrant") {
//
//            val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
//            val position = RadarCoordinate(100.0, 135.0)
//
//            (0..(radarParameters.seekTimeSec * S_TO_US).toInt())
//                .map { tUs -> TWO_PI / cParams.rotationTimeUs * tUs }
//                .forEach { sweepHeadingRad ->
//                    calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)
//                }
//
//            it("should result in detection") {
//                hits.nextSetBit(0) shouldNotEqual -1
//            }
//        }
//
//        on("calculating the 3rd quadrant") {
//
//            val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
//            val position = RadarCoordinate(100.0, 225.0)
//
//            (0..(radarParameters.seekTimeSec * S_TO_US).toInt())
//                .map { tUs -> TWO_PI / cParams.rotationTimeUs * tUs }
//                .forEach { sweepHeadingRad ->
//                    calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)
//                }
//
//            it("should result in detection") {
//                hits.nextSetBit(0) shouldNotEqual -1
//            }
//        }
//
//        on("calculating the 4th quadrant") {
//
//            val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
//            val position = RadarCoordinate(100.0, 315.0)
//
//            (0..(radarParameters.seekTimeSec * S_TO_US).toInt())
//                .map { tUs -> TWO_PI / cParams.rotationTimeUs * tUs }
//                .forEach { sweepHeadingRad ->
//                    calculatePointTargetHits(hits, position, sweepHeadingRad, cParams)
//                }
//
//            it("should result in detection") {
//                hits.nextSetBit(0) shouldNotEqual -1
//            }
//        }
//    }
//
//    given("A clutter map in detection range with hdg in [270, 90]") {
//
//        val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
//        val width = round(2.0 * radarParameters.maxRadarDistanceKm / radarParameters.distanceResolutionKm).toInt()
//        val height = round(2.0 * radarParameters.maxRadarDistanceKm / radarParameters.distanceResolutionKm).toInt()
//        val clutterMap = WritableImage(width, height)
//        clutterMap.pixelWriter.setColor(width / 4, height / 4, Color.WHITE)
//
//        on("calculating the hit") {
//
//            calculateClutterHits(RasterIterator(clutterMap), cParams)
//
//            it("should result in  detection") {
//                hits.nextSetBit(0) shouldNotEqual -1
//            }
//        }
//    }


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