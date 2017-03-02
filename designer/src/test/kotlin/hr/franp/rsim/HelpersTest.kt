@file:Suppress("unused")

package hr.franp.rsim

import hr.franp.*
import hr.franp.rsim.models.*
import javafx.scene.image.*
import javafx.scene.paint.*
import org.amshove.kluent.*
import org.jetbrains.spek.api.*
import org.jetbrains.spek.api.dsl.*
import java.lang.Math.*

class HelpersTest : Spek({

    val radarParameters = RadarParameters().apply {
        impulsePeriodUs = 3003.0
        maxImpulsePeriodUs = 3072.0
        seekTimeSec = 12.0
        azimuthChangePulse = 4096
        horizontalAngleBeamWidthDeg = 1.4
        distanceResolutionKm = 0.150
        maxRadarDistanceKm = 400.0
        minRadarDistanceKm = 5.0
    }

    val rotationTimeUs = radarParameters.seekTimeSec * S_TO_US


    given("A point target in distance detection range") {

        val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
        val position = RadarCoordinate(100.0, 10.0)

        beforeEachTest { hits.clear() }


        on("calculating the hit for sweep angle just prior heading range") {

            val tUs = toRadians(position.azDeg - radarParameters.horizontalAngleBeamWidthDeg) * rotationTimeUs / TWO_PI

            calculatePointTargetHits(hits, position, tUs, radarParameters)

            it("should result in no detection") {
                hits.nextSetBit(0) shouldEqual -1
            }
        }

        on("calculating the hit for sweep angle just after heading range") {

            val tUs = toRadians(position.azDeg + radarParameters.horizontalAngleBeamWidthDeg) * rotationTimeUs / TWO_PI

            calculatePointTargetHits(hits, position, tUs, radarParameters)

            it("should result in no detection") {
                hits.nextSetBit(0) shouldEqual -1
            }
        }

        on("calculating the hit for sweep angle inside heading range") {

            val tUs = toRadians(position.azDeg) * rotationTimeUs / TWO_PI

            calculatePointTargetHits(hits, position, tUs, radarParameters)

            it("should result in detection") {
                hits.nextSetBit(0) shouldNotEqual -1
            }
        }
    }

    given("A point target outside distance detection range") {

        val hits = Bits((radarParameters.azimuthChangePulse * radarParameters.maxImpulsePeriodUs).toInt())
        val azDeg = 10.0
        val tUs = toRadians(azDeg) * rotationTimeUs / TWO_PI

        beforeEachTest { hits.clear() }

        on("calculating the close target hit") {

            val position = RadarCoordinate(radarParameters.minRadarDistanceKm - 1, azDeg)

            calculatePointTargetHits(hits, position, tUs, radarParameters)

            it("should result in no detection") {
                hits.nextSetBit(0) shouldEqual -1
            }
        }

        on("calculating the far target hit") {

            val position = RadarCoordinate(radarParameters.maxRadarDistanceKm + 1, azDeg)

            calculatePointTargetHits(hits, position, tUs, radarParameters)

            it("should result in no detection") {
                hits.nextSetBit(0) shouldEqual -1
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

            calculateClutterHits(hits, RasterIterator(clutterMap), radarParameters)

            it("should result in  detection") {
                hits.nextSetBit(0) shouldNotEqual -1
            }
        }
    }
})