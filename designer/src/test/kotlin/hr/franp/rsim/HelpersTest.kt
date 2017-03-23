@file:Suppress("unused")

package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.embed.swing.*
import javafx.geometry.*
import javafx.scene.image.*
import javafx.scene.paint.*
import org.amshove.kluent.*
import org.jetbrains.spek.api.*
import org.jetbrains.spek.api.dsl.*
import java.lang.Math.*
import java.nio.*
import java.util.*
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

        val buffer = ByteBuffer.allocate(FILE_HEADER_BYTE_CNT + 2 * cParams.arpByteCnt.toInt())
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

        val buffer = ByteBuffer.allocate(FILE_HEADER_BYTE_CNT + cParams.arpByteCnt.toInt())
            .order(ByteOrder.LITTLE_ENDIAN)

        on("calculating the hit for sweep angle just prior heading range") {

            val sweepHeadingDeg = position.azDeg - radarParameters.horizontalAngleBeamWidthDeg
            val tUs = sweepHeadingDeg / 360.0 * cParams.rotationTimeUs
            val bitSet = BitSet.valueOf(
                buffer.calculatePointTargetHits(pathSegment, tUs, tUs, cParams, false)
            )

            it("should result in no detection") {
                bitSet.nextSetBit(0) shouldEqualTo -1
            }
        }

        on("calculating the hit for sweep angle just after heading range") {

            val sweepHeadingDeg = position.azDeg + radarParameters.horizontalAngleBeamWidthDeg
            val tUs = sweepHeadingDeg / 360.0 * cParams.rotationTimeUs
            val bitSet = BitSet.valueOf(
                buffer.calculatePointTargetHits(pathSegment, tUs, tUs, cParams, false)
            )

            it("should result in no detection") {
                bitSet.nextSetBit(0) shouldEqualTo -1
            }
        }

        on("calculating the hit for sweep angle inside heading range") {

            val sweepHeadingDeg = position.azDeg
            val tUs = sweepHeadingDeg / 360.0 * cParams.rotationTimeUs
            val hits = buffer.calculatePointTargetHits(pathSegment, tUs - cParams.rotationTimeUs, tUs + cParams.rotationTimeUs, cParams, false)
                .toAcpTigPairs(cParams)
                .toList()

            it("should result in detection") {
                hits.count() shouldEqual 1
                hits[0].first shouldEqualTo (position.azDeg * cParams.degToAcp).toLong()
                hits[0].second shouldEqualTo (position.rKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM).toLong()
            }
        }
    }

    given("a point target outside distance detection range") {

        val azDeg = 10.0
        val sweepHeadingRad = toRadians(azDeg)

        val buffer = ByteBuffer.allocate(FILE_HEADER_BYTE_CNT + cParams.arpByteCnt.toInt())
            .order(ByteOrder.LITTLE_ENDIAN)

        beforeEachTest { buffer.clean() }

        on("calculating the close target hit") {

            val position = RadarCoordinate(radarParameters.minRadarDistanceKm - 1, azDeg)
            val pathSegment = PathSegment(
                p1 = position,
                p2 = position,
                t1Us = 0.0,
                t2Us = radarParameters.seekTimeSec * S_TO_US,
                vxKmUs = 0.0,
                vyKmUs = 0.0,
                type = MovingTargetType.Point
            )
            val tUs = sweepHeadingRad / TWO_PI * cParams.rotationTimeUs
            val bitSet = BitSet.valueOf(
                buffer.calculatePointTargetHits(pathSegment, tUs, tUs, cParams, false)
            )

            it("should result in no detection") {
                bitSet.nextSetBit(0) shouldEqual -1
            }
        }

        on("calculating the far target hit") {

            val position = RadarCoordinate(radarParameters.maxRadarDistanceKm + 1, azDeg)
            val pathSegment = PathSegment(
                p1 = position,
                p2 = position,
                t1Us = 0.0,
                t2Us = radarParameters.seekTimeSec * S_TO_US,
                vxKmUs = 0.0,
                vyKmUs = 0.0,
                type = MovingTargetType.Point
            )
            val tUs = sweepHeadingRad / TWO_PI * cParams.rotationTimeUs
            val bitSet = BitSet.valueOf(
                buffer.calculatePointTargetHits(pathSegment, tUs, tUs, cParams, false)
            )

            it("should result in no detection") {
                bitSet.nextSetBit(0) shouldEqual -1
            }
        }

    }

    given("targets in all quadrants") {

        val buffer = ByteBuffer.allocate(FILE_HEADER_BYTE_CNT + cParams.arpByteCnt.toInt())
            .order(ByteOrder.LITTLE_ENDIAN)

        val p1 = RadarCoordinate(100.0, 45.0)
        val ps1 = PathSegment(
            p1 = p1,
            p2 = p1,
            t1Us = 0.0,
            t2Us = radarParameters.seekTimeSec * S_TO_US,
            vxKmUs = 0.0,
            vyKmUs = 0.0,
            type = MovingTargetType.Point
        )

        val p2 = RadarCoordinate(100.0, 135.0)
        val ps2 = PathSegment(
            p1 = p2,
            p2 = p2,
            t1Us = 0.0,
            t2Us = radarParameters.seekTimeSec * S_TO_US,
            vxKmUs = 0.0,
            vyKmUs = 0.0,
            type = MovingTargetType.Point
        )

        val p3 = RadarCoordinate(100.0, 225.0)
        val ps3 = PathSegment(
            p1 = p3,
            p2 = p3,
            t1Us = 0.0,
            t2Us = radarParameters.seekTimeSec * S_TO_US,
            vxKmUs = 0.0,
            vyKmUs = 0.0,
            type = MovingTargetType.Point
        )

        val p4 = RadarCoordinate(100.0, 315.0)
        val ps4 = PathSegment(
            p1 = p4,
            p2 = p4,
            t1Us = 0.0,
            t2Us = radarParameters.seekTimeSec * S_TO_US,
            vxKmUs = 0.0,
            vyKmUs = 0.0,
            type = MovingTargetType.Point
        )

        beforeEachTest { buffer.clean() }

        on("calculating the 1st quadrant") {

            buffer.calculatePointTargetHits(ps1, 0.0, cParams.rotationTimeUs, cParams, false)

            it("should result in detection") {
                val bitSet = BitSet.valueOf(buffer)
                bitSet.nextSetBit(0) shouldNotEqual -1
            }
        }

        on("calculating the 2nd quadrant") {

            buffer.calculatePointTargetHits(ps2, 0.0, cParams.rotationTimeUs, cParams, false)

            it("should result in detection") {
                val bitSet = BitSet.valueOf(buffer)
                bitSet.nextSetBit(0) shouldNotEqual -1
            }
        }

        on("calculating the 3rd quadrant") {

            buffer.calculatePointTargetHits(ps3, 0.0, cParams.rotationTimeUs, cParams, false)

            it("should result in detection") {
                val bitSet = BitSet.valueOf(buffer)
                bitSet.nextSetBit(0) shouldNotEqual -1
            }
        }

        on("calculating the 4th quadrant") {

            buffer.calculatePointTargetHits(ps4, 0.0, cParams.rotationTimeUs, cParams, false)

            it("should result in detection") {
                val bitSet = BitSet.valueOf(buffer)
                bitSet.nextSetBit(0) shouldNotEqual -1
            }
        }

        on("performing the round trip calc") {

            buffer.calculatePointTargetHits(ps1, 0.0, cParams.rotationTimeUs, cParams, false)
            buffer.calculatePointTargetHits(ps2, 0.0, cParams.rotationTimeUs, cParams, false)
            buffer.calculatePointTargetHits(ps3, 0.0, cParams.rotationTimeUs, cParams, false)
            buffer.calculatePointTargetHits(ps4, 0.0, cParams.rotationTimeUs, cParams, false)
            buffer.rewind()

            it("should result in detection") {
                val pts = listOf(p1, p2, p3, p4)
                    .map { it.toCartesian() }

                val ri = RasterIterator(SwingFXUtils.toFXImage(
                    buffer.toCompressedHitImage(cParams),
                    null
                ))

                val wh = cParams.maxRadarDistanceKm
                val hitPts = mutableListOf<Point2D>()
                while (ri.hasNext()) {
                    val cartHit = ri.next() ?: continue
                    hitPts.add(cartHit.add(-wh, -wh))
                }

                val minDistance = pts
                    .map { p -> hitPts.map { it.distance(p) }.min() ?: Double.NaN }
                    .max() ?: Double.NaN

                minDistance shouldBeLessOrEqualTo 2.0
            }
        }
    }

    given("a clutter map with targets in all quadrants") {

        val buffer = ByteBuffer.allocate(FILE_HEADER_BYTE_CNT + cParams.arpByteCnt.toInt())
            .order(ByteOrder.LITTLE_ENDIAN)

        beforeEachTest { buffer.clean() }

        val width = round(2.0 * radarParameters.maxRadarDistanceKm).toInt()
        val height = round(2.0 * radarParameters.maxRadarDistanceKm).toInt()
        val clutterMap = WritableImage(width, height)
        val quad = width / 4
        val half = width / 2
        val cpQ1 = Point2D(quad.toDouble(), quad.toDouble())
        val cpQ2 = cpQ1.add(half.toDouble(), 0.0)
        val cpQ3 = cpQ1.add(half.toDouble(), half.toDouble())
        val cpQ4 = cpQ1.add(0.0, half.toDouble())

        clutterMap.pixelWriter.setColor(cpQ1.x.toInt(), cpQ1.y.toInt(), Color.WHITE)
        clutterMap.pixelWriter.setColor(cpQ2.x.toInt(), cpQ2.y.toInt(), Color.WHITE)
        clutterMap.pixelWriter.setColor(cpQ3.x.toInt(), cpQ3.y.toInt(), Color.WHITE)
        clutterMap.pixelWriter.setColor(cpQ4.x.toInt(), cpQ4.y.toInt(), Color.WHITE)

        on("calculating the hit") {

            buffer.calculateClutterMapHits(
                hitRaster = RasterIterator(clutterMap),
                cParams = cParams,
                scale = 1.0
            )

            it("should result in detection") {
                val quadrant = cParams.azimuthChangePulseCount / 4

                buffer.rewind()
                val quadrants = buffer.toAcpTigPairs(cParams)
                    .map { it.first / quadrant }
                    .distinct()
                    .sorted()
                    .toList()

                quadrants shouldContain 0
                quadrants shouldContain 1
                quadrants shouldContain 2
                quadrants shouldContain 3
            }
        }

        on("performing the round trip calc") {

            buffer.calculateClutterMapHits(
                hitRaster = RasterIterator(clutterMap),
                cParams = cParams,
                scale = 1.0
            )
            buffer.rewind()

            it("should result in detection") {
                val pts = listOf(cpQ1, cpQ2, cpQ3, cpQ4)

                val ri = RasterIterator(SwingFXUtils.toFXImage(
                    buffer.toCompressedHitImage(cParams),
                    null
                ))

                val hitPts = mutableListOf<Point2D>()
                while (ri.hasNext()) {
                    val cartHit = ri.next() ?: continue
                    hitPts.add(cartHit)
                }

                val minDistance = pts
                    .map { p -> hitPts.map { it.distance(p) }.min() ?: Double.NaN }
                    .max() ?: Double.NaN

                minDistance shouldBeLessOrEqualTo 5.0
            }
        }

    }

    given("an offset clutter map") {

        val buffer = ByteBuffer.allocate(FILE_HEADER_BYTE_CNT + cParams.arpByteCnt.toInt())
            .order(ByteOrder.LITTLE_ENDIAN)

        beforeEachTest { buffer.clean() }

        val clutterMap = WritableImage(100, 100)
        val cp1 = Point2D(50.0, 50.0)
        val cp2 = cp1.add(30.0, 10.0)
        val cp3 = cp1.add(15.0, 3.0)

        clutterMap.pixelWriter.setColor(cp1.x.toInt(), cp1.y.toInt(), Color.WHITE)
        clutterMap.pixelWriter.setColor(cp2.x.toInt(), cp2.y.toInt(), Color.WHITE)
        clutterMap.pixelWriter.setColor(cp3.x.toInt(), cp3.y.toInt(), Color.WHITE)

        val offset = RadarCoordinate(300.0, 33.0)
        val offsetCart = offset.toCartesian()

        on("performing the round trip calc") {

            buffer.calculateClutterMapHits(
                hitRaster = RasterIterator(clutterMap),
                cParams = cParams,
                origin = offset,
                scale = 1.0
            )

            it("should result in detection") {
                val pts = listOf(cp1, cp2, cp3)
                    .map { it.add(offsetCart.x, offsetCart.y) }

                val ri = RasterIterator(SwingFXUtils.toFXImage(
                    buffer.toCompressedHitImage(cParams),
                    null
                ))

                val hitPts = mutableListOf<Point2D>()
                ri.forEach {
                    val cartHit = it ?: return@forEach
                    hitPts.add(cartHit)
                }

                val minDistance = pts
                    .map { p -> hitPts.map { it.distance(p) }.min() ?: Double.NaN }
                    .max() ?: Double.NaN

                minDistance shouldBeLessOrEqualTo 5.0
            }
        }

    }

    given("a set of ACP/TRIG pairs") {
        val list = listOf(
            Pair(radarParameters.azimuthChangePulse * 10, 0.1 * cParams.maxSignalTimeUs),
            Pair(radarParameters.azimuthChangePulse * 20, 0.5 * cParams.maxSignalTimeUs),
            Pair(radarParameters.azimuthChangePulse * 30, 0.7 * cParams.maxSignalTimeUs),
            Pair(radarParameters.azimuthChangePulse * 40, 0.8 * cParams.maxSignalTimeUs),
            Pair(radarParameters.azimuthChangePulse * 599, 0.9 * cParams.maxSignalTimeUs)
        )
        val buffer = ByteBuffer.allocate(FILE_HEADER_BYTE_CNT + 600 * cParams.arpByteCnt.toInt())
            .order(ByteOrder.LITTLE_ENDIAN)

        on("writing hits") {
            list.forEach { pair -> buffer.writeHit(pair.first, pair.second.toInt(), cParams) }

            it("should have written them in correct positions") {
                list.forEach { pair ->
                    val pos = (FILE_HEADER_BYTE_CNT + pair.first * cParams.acpByteCnt + pair.second / 8).toInt()
                    buffer.get(pos) shouldNotEqualTo 0b0
                }
            }
        }

        on("writing spread hits") {
            list.forEach { pair -> buffer.writeHit(pair.first, pair.second.toInt(), cParams) }

            val bufferSpread = ByteBuffer.allocate(FILE_HEADER_BYTE_CNT + 600 * cParams.arpByteCnt.toInt())
                .order(ByteOrder.LITTLE_ENDIAN)

            buffer.spreadHits(bufferSpread, cParams)

            it("should have written them in correct positions") {
                list.forEach { pair ->
                    val pos = (FILE_HEADER_BYTE_CNT + pair.first * cParams.acpByteCnt + pair.second / 8).toInt()
                    bufferSpread.get(pos) shouldNotEqualTo 0b0
                }
            }
        }
    }

    given("a zoomed in cartesian region in quadrant 1") {

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

    given("a zoomed in cartesian region in quadrant 2") {

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

    given("a zoomed in cartesian region in quadrant 3") {

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

    given("a zoomed in cartesian region in quadrant 4") {

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

    given("a zoomed in cartesian region in covering all quadrants") {

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