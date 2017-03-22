package hr.franp.rsim

import hr.franp.rsim.models.*
import javafx.beans.property.*
import javafx.event.*
import javafx.geometry.*
import javafx.scene.canvas.*
import javafx.scene.image.*
import javafx.scene.layout.*
import javafx.scene.paint.*
import javafx.scene.shape.*
import javafx.scene.text.*
import javafx.scene.transform.*
import org.apache.commons.collections4.map.*
import tornadofx.*
import java.lang.Math.*
import java.time.*
import java.time.format.*
import java.util.*
import java.util.stream.*

class RadarScreenView : View() {

    /**
     * Current time in the simulation
     */
    val simulatedCurrentTimeSecProperty = SimpleDoubleProperty(0.0)

    var displayParameters by property(readConfig())

    val displayParametersProperty = getProperty(RadarScreenView::displayParameters)

    private var combinedTransform: Transform = Affine()
    private var invCombinedTransform: Transform = combinedTransform.createInverse()

    private val cloudOneImage = processHitMaskImage(Image(resources["/cloud1.png"]))
    private val cloudTwoImage = processHitMaskImage(Image(resources["/cloud2.png"]))

    val mousePositionProperty = SimpleObjectProperty<RadarCoordinate>()
    val mouseClickProperty = SimpleObjectProperty<RadarCoordinate>()

    val selectionRect = Rectangle().apply {
        fill = Color.DARKGRAY.deriveColor(3.0, 1.0, 1.0, 0.4)
    }

    override val root = Pane().apply {
        addClass(Styles.radarScreen)
    }

    private val designerController: DesignerController by inject()
    private val simulatorController: SimulatorController by inject()

    private val angleStringConverter = AngleStringConverter()
    private val speedStringConverter = SpeedStringConverter()
    private val distanceStringConverter = DistanceStringConverter()

    val sketch = Sketch {
        mouseClicked {
            val displayPoint = invCombinedTransform.transform(it.x, it.y)
            mouseClickProperty.set(RadarCoordinate.fromCartesian(displayPoint.x, displayPoint.y))
        }
        mouseMoved {
            val displayPoint = invCombinedTransform.transform(it.x, it.y)
            mousePositionProperty.set(RadarCoordinate.fromCartesian(displayPoint.x, displayPoint.y))
        }
        draw {
            if (simulatorController.simulationRunningProperty.get()) {
                simulatedCurrentTimeSecProperty.set(simulatorController.approxSimTime())
            }
            clearRect(0.0, 0.0, width, height)
            drawScene(this)
        }
    }

    init {

        with(root) {

            addSelectionRectangleGesture(this, selectionRect, EventHandler {
                if (it.isConsumed) {
                    displayParameters = displayParameters.copy(
                        viewPort = if (selectionRect.width == 0.0 && selectionRect.height == 0.0) {
                            null
                        } else {
                            invCombinedTransform.transform(selectionRect.boundsInLocal)
                        }
                    )
                }
            })

            root.children.add(Canvas2D(sketch).apply {
                widthProperty().bind(root.widthProperty())
                heightProperty().bind(root.heightProperty())
                start()
            })
        }

        // config
        writeConfig()
        displayParametersProperty.addListener { _, _, _ ->
            writeConfig()
        }

    }

    /**
     * Write the view config to file.
     */
    private fun writeConfig() {
        config["distanceStep"] = displayParameters.distanceStep.toString()
        config["distanceUnit"] = displayParameters.distanceUnit.toString()
        config["azimuthSteps"] = displayParameters.azimuthSteps.toString()
        config["azimuthMarkerType"] = displayParameters.azimuthMarkerType.toString()
        config["coordinateSystem"] = displayParameters.coordinateSystem.toString()
        config["targetLayerOpacity"] = displayParameters.targetLayerOpacity.toString()
        config["targetHitLayerOpacity"] = displayParameters.targetHitLayerOpacity.toString()
        config["clutterLayerOpacity"] = displayParameters.clutterLayerOpacity.toString()
        config["plotHistoryCount"] = displayParameters.plotHistoryCount.toString()
        config.save()
    }

    /**
     * Read the view config from file.
     */
    private fun readConfig(): DisplayParameters = DisplayParameters(
        distanceStep = config.double("distanceStep") ?: 50.0,
        distanceUnit = DistanceUnit.valueOf(
            config.string("distanceUnit", DistanceUnit.Km.toString())
        ),
        azimuthSteps = config.string("azimuthSteps")?.toInt() ?: 36,
        azimuthMarkerType = AzimuthMarkerType.valueOf(
            config.string("azimuthMarkerType", AzimuthMarkerType.FULL.toString())
        ),
        coordinateSystem = CoordinateSystem.valueOf(
            config.string("coordinateSystem", CoordinateSystem.R_AZ.toString())
        ),
        viewPort = null,
        targetDisplayFilter = emptySequence(),
        targetLayerOpacity = config.double("targetLayerOpacity") ?: 1.0,
        targetHitLayerOpacity = config.double("targetHitLayerOpacity") ?: 1.0,
        clutterLayerOpacity = config.double("clutterLayerOpacity") ?: 1.0,
        plotHistoryCount = config.string("plotHistoryCount", "6")?.toInt() ?: 6
    )


    /**
     * Calculates the scale of conversion between the selected display units and the native Km
     */
    private fun distanceToKmScale() = if (displayParameters.distanceUnit == DistanceUnit.NM)
        1.852
    else
        1.0


    private fun setupViewPort() {

        val viewPort = displayParameters.viewPort ?: BoundingBox(
            -simulatorController.radarParameters.maxRadarDistanceKm * 1.1,
            -simulatorController.radarParameters.maxRadarDistanceKm * 1.1,
            2 * simulatorController.radarParameters.maxRadarDistanceKm * 1.1,
            2 * simulatorController.radarParameters.maxRadarDistanceKm * 1.1
        )

        val destViewPort = BoundingBox(0.0, 0.0, root.width, root.height)

        combinedTransform = setupViewPort(viewPort, destViewPort)
        invCombinedTransform = combinedTransform.createInverse()

    }

    private fun getCurrentPathSegment(movingTarget: MovingTarget, currentTimeUs: Double): PathSegment? {

        var p1 = movingTarget.initialPosition
        var t1 = 0.0

        var ps: PathSegment? = null

        if (movingTarget.directions.size == 0) {
            ps = PathSegment(
                p1 = p1,
                p2 = p1,
                t1Us = t1,
                t2Us = designerController.scenario.simulationDurationMin * MIN_TO_US,
                vxKmUs = 0.0,
                vyKmUs = 0.0,
                type = movingTarget.type
            )
        } else {
            run breaker@ {
                movingTarget.directions
                    .forEach { direction ->
                        val p2 = direction.destination
                        val speedKmUs = direction.speedKmh / HOUR_TO_US

                        // distance from last course change point
                        val p1c = p1.toCartesian()
                        val p2c = p2.toCartesian()
                        val dx = p2c.x - p1c.x
                        val dy = p2c.y - p1c.y
                        val distance = sqrt(pow(dx, 2.0) + pow(dy, 2.0))
                        val dt = distance / speedKmUs
                        if (currentTimeUs >= t1 && currentTimeUs < t1 + dt) {
                            ps = PathSegment(
                                p1 = p1,
                                p2 = p2,
                                t1Us = t1,
                                t2Us = t1 + dt,
                                vxKmUs = speedKmUs * dx / distance,
                                vyKmUs = speedKmUs * dy / distance,
                                type = movingTarget.type
                            )
                            return@breaker
                        }

                        p1 = p2
                        t1 += dt

                    }
            }
        }

        return ps

    }

    fun configDistanceDisplay(distanceUnit: DistanceUnit, step: Double? = 0.0) {
        displayParameters = displayParameters.copy(
            distanceUnit = distanceUnit,
            distanceStep = step ?: displayParameters.distanceStep
        )
    }

    fun configAzimuthDisplay(azimuthMarkerType: AzimuthMarkerType, step: Int? = 0) {
        displayParameters = displayParameters.copy(
            azimuthMarkerType = azimuthMarkerType,
            azimuthSteps = step ?: displayParameters.azimuthSteps
        )
    }

    fun configTargetLayerOpacity(opacity: Double) {
        displayParameters = displayParameters.copy(
            targetLayerOpacity = opacity
        )
    }

    fun configTargetHitLayerOpacity(opacity: Double) {
        displayParameters = displayParameters.copy(
            targetHitLayerOpacity = opacity
        )
    }

    fun configClutterLayerOpacity(opacity: Double) {
        displayParameters = displayParameters.copy(
            clutterLayerOpacity = opacity
        )
    }

    fun drawScene(gc: GraphicsContext) {
        setupViewPort()
        drawClutterMap(gc)
        drawStaticMarkers(gc)
        drawDynamicMarkers(gc)
        drawMovingTargets(gc)
        drawTargetHits(gc)
        drawUI(gc)
    }

    private val dateFormatPattern = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatPattern = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val angleConverter = AngleStringConverter()

    private fun drawUI(gc: GraphicsContext) {

        val height = 60.0
        val width = 200.0
        val offset = 10.0

        val originalFont = gc.font
        gc.font = Font(30.0)
        gc.textAlign = TextAlignment.CENTER
        gc.textBaseline = VPos.CENTER

        // display time in upper left
        gc.fill = Styles.radarFgColor
        gc.fillRect(offset, offset, width, height)
        gc.fill = Styles.radarBgColor
        gc.fillText(
            LocalTime.now().format(timeFormatPattern),
            offset + width / 2,
            offset + height / 2,
            width - offset
        )

        // display date in upper right
        gc.fill = Styles.radarFgColor
        gc.fillRect(sketch.width - offset - width, offset, width, height)
        gc.fill = Styles.radarBgColor
        gc.fillText(
            LocalDate.now().format(dateFormatPattern),
            sketch.width - offset - width / 2,
            offset + height / 2,
            width - offset
        )

        // display antenna azimuth in lower left
        gc.fill = Styles.radarFgColor
        gc.fillRect(offset, sketch.height - offset - height, width, height)
        gc.fill = Styles.radarBgColor
        gc.fillText(
            angleConverter.toString(
                normalizeAngleDeg(
                    360.0 * (simulatedCurrentTimeSecProperty.get() / simulatorController.radarParameters.seekTimeSec)
                )
            ),
            offset + width / 2,
            sketch.height - offset - height / 2,
            width - offset
        )

        // display simulation time in lower right
        gc.fill = Styles.radarFgColor
        gc.fillRect(sketch.width - offset - width, sketch.height - offset - height, width, height)
        gc.fill = Styles.radarBgColor
        gc.fillText(
            LocalTime.MIDNIGHT
                .plus(
                    Duration.ofSeconds(simulatedCurrentTimeSecProperty.get().toLong())
                )
                .format(timeFormatPattern),
            sketch.width - offset - width / 2,
            sketch.height - offset - height / 2,
            width - offset
        )

        gc.font = originalFont
    }

    fun drawStaticMarkers(gc: GraphicsContext) {

        gc.stroke = Styles.radarFgColor
        gc.fill = Styles.radarFgColor

        // scale in case of nautical miles
        val distanceToKmScale = distanceToKmScale()

        // Distance markers
        val stepKm = if (displayParameters.distanceStep == 0.0)
            simulatorController.radarParameters.maxRadarDistanceKm
        else
            displayParameters.distanceStep * distanceToKmScale

        val mandatoryDistanceMarkers = sequenceOf(
            simulatorController.radarParameters.minRadarDistanceKm,
            simulatorController.radarParameters.maxRadarDistanceKm
        )
        val distanceSequence = mandatoryDistanceMarkers + generateSequence(0.0) { it + stepKm }
            .dropWhile { it <= simulatorController.radarParameters.minRadarDistanceKm }
            .takeWhile { it <= simulatorController.radarParameters.maxRadarDistanceKm - stepKm / 3 }

        val cp = combinedTransform.transform(0.0, 0.0)
        for (r in distanceSequence) {
            val dp = combinedTransform.transform(r, 0.0)
            val dist = cp.distance(dp)
            gc.strokeOval(cp.x - dist, cp.y - dist, 2 * dist, 2 * dist)

            gc.textAlign = TextAlignment.CENTER
            gc.textBaseline = VPos.TOP
            gc.fillText(
                round(r / distanceToKmScale).toInt().toString(),
                dp.x,
                dp.y
            )
        }

        // draw angle markers
        val azimuthSequence = if (displayParameters.azimuthSteps >= 0) {
            val deltaAngleStep = TWO_PI / displayParameters.azimuthSteps
            generateSequence(0.0) { it + deltaAngleStep }
                .takeWhile { it < (TWO_PI - deltaAngleStep / 3) }
        } else {
            sequenceOf(0.0, HALF_PI, PI, 3 * HALF_PI, TWO_PI)
        }

        if (displayParameters.azimuthMarkerType == AzimuthMarkerType.FULL) {
            // draw full lines (like a spiderweb)
            azimuthSequence.forEach { azimuth ->
                val angle = azimuthToAngle(azimuth)
                val p1 = combinedTransform.transform(
                    simulatorController.radarParameters.minRadarDistanceKm * cos(angle),
                    simulatorController.radarParameters.minRadarDistanceKm * sin(angle)
                )
                val p2 = combinedTransform.transform(
                    simulatorController.radarParameters.maxRadarDistanceKm * cos(angle),
                    simulatorController.radarParameters.maxRadarDistanceKm * sin(angle)
                )

                gc.strokeLine(
                    p1.x, p1.y,
                    p2.x, p2.y
                )
            }
        } else {
            // draw only ticks on distance circles
            azimuthSequence.forEach { a ->
                distanceSequence
                    .filter { it > simulatorController.radarParameters.minRadarDistanceKm }
                    .forEach { d ->

                        val length = 5
                        val pc = combinedTransform
                            .transform(
                                d * Math.cos(a),
                                d * Math.sin(a)
                            )
                        val p1 = pc.add(
                            -length / 2.0 * Math.cos(a),
                            length / 2.0 * Math.sin(a)
                        )
                        val p2 = pc.add(
                            length / 2.0 * Math.cos(a),
                            -length / 2.0 * Math.sin(a)
                        )

                        gc.strokeLine(
                            p1.x, p1.y,
                            p2.x, p2.y
                        )
                    }
            }
        }

        azimuthSequence.forEach { azimuth ->
            val angle = azimuthToAngle(azimuth)
            val p = combinedTransform
                .transform(
                    simulatorController.radarParameters.maxRadarDistanceKm * cos(angle),
                    simulatorController.radarParameters.maxRadarDistanceKm * sin(angle)
                )
                .add(
                    20 * cos(angle),
                    -20 * sin(angle)
                )

            gc.textAlign = TextAlignment.CENTER
            gc.textBaseline = VPos.CENTER
            gc.fillText(
                round(toDegrees(azimuth)).toInt().toString(),
                p.x,
                p.y
            )
        }
    }

    fun drawDynamicMarkers(gc: GraphicsContext) {

        val angleRad = azimuthToAngle(
            TWO_PI * (simulatedCurrentTimeSecProperty.get() / simulatorController.radarParameters.seekTimeSec)
        )

        // draw simulation position markers
        val p = combinedTransform.transform(
            simulatorController.radarParameters.maxRadarDistanceKm * cos(angleRad),
            simulatorController.radarParameters.maxRadarDistanceKm * sin(angleRad)
        )
        gc.fill = Color.RED.deriveColor(0.0, 1.0, 1.0, 0.5)
        gc.fillOval(p.x - 5.0, p.y - 5.0, 10.0, 10.0)

    }

    val map: HashMap<Triple<Int, Int, Clutter>, Image> = HashMap()
    fun drawClutterMap(gc: GraphicsContext) {

        val clutter = designerController.scenario.clutter ?: return

        // draw stationary targets
        val width = (2.0 * simulatorController.radarParameters.maxRadarDistanceKm)
        val height = (2.0 * simulatorController.radarParameters.maxRadarDistanceKm)

        val key = Triple(width.toInt(), height.toInt(), clutter)
        if (!map.containsKey(key)) {
            map.put(
                key,
                clutter.getImage(width.toInt(), height.toInt()) ?: return
            )
            log.info { "Computed new clutter map for size ($width,$height)" }
        }
        val rasterMapImage = map[key] ?: return

        val transformedBounds = combinedTransform.transform(BoundingBox(
            -width / 2.0,
            -height / 2.0,
            width,
            height
        ))

        val alpha = gc.globalAlpha
        gc.globalAlpha = displayParameters.clutterLayerOpacity
        gc.drawImage(
            rasterMapImage,
            transformedBounds.minX,
            transformedBounds.minY,
            transformedBounds.width,
            transformedBounds.height
        )
        gc.globalAlpha = alpha
    }

    fun drawMovingTargets(gc: GraphicsContext) {

        val currentTimeSec = simulatedCurrentTimeSecProperty.get()
        val currentTimeUs = S_TO_US * currentTimeSec

        // draw moving targets
        val movingTargetCount = designerController.scenario?.movingTargets?.size ?: return
        val selectedTarget = designerController.selectedMovingTarget
        designerController.scenario.movingTargets
            .sortedBy { it.name }
            // show only if the target is selected or marked as display
            .filter { it == designerController.selectedMovingTarget || displayParameters.targetDisplayFilter.none() || it.name in displayParameters.targetDisplayFilter }
            .forEachIndexed { i, target ->

                val type = target.type ?: return@forEachIndexed

                val initPosCart = target.initialPosition.toCartesian()
                val p = combinedTransform.transform(
                    initPosCart.x,
                    initPosCart.y
                )

                val selectedTargetOpacityFactor = if (selectedTarget == null || selectedTarget == target) {
                    1.0
                } else {
                    0.2
                }

                // shift colors for each target and if the target is selected display other targets semi-transparent
                val color = Color.RED.deriveColor(
                    i * (360.0 / movingTargetCount),
                    1.0,
                    1.0,
                    displayParameters.targetLayerOpacity * selectedTargetOpacityFactor
                )

                // draw course change point
                gc.stroke = color
                gc.fill = color
                gc.fillOval(p.x - 0.5, p.y - 0.5, 1.0, 1.0)

                // draw segments and point markers
                var pFrom = Point2D(p.x, p.y)
                target.directions.forEach { d ->
                    val dpc = combinedTransform.transform(d.destination.toCartesian())

                    gc.stroke = color
                    gc.strokeLine(
                        pFrom.x, pFrom.y,
                        dpc.x, dpc.y
                    )
                    gc.fill = color
                    gc.fillOval(p.x - 1.0, p.y - 1.0, 2.0, 2.0)

                    pFrom = dpc
                }


                // draw current simulated position marker
                val plotPathSegment = getCurrentPathSegment(target, currentTimeUs) ?: return@forEachIndexed
                val plotPos = plotPathSegment.getPositionForTime(currentTimeUs) ?: return@forEachIndexed
                val plotPosCart = plotPos.toCartesian()
                val pt = combinedTransform.transform(plotPosCart)

                val distanceKm = sqrt(pow(plotPosCart.x, 2.0) + pow(plotPosCart.y, 2.0))
                val az = toDegrees(angleToAzimuth(atan2(plotPosCart.y, plotPosCart.x)))

                val text = """${target.name}
hdg=${angleStringConverter.toString(plotPathSegment.headingDeg)}
spd=${speedStringConverter.toString(plotPathSegment.vKmh)}
r=${distanceStringConverter.toString(distanceKm / distanceToKmScale())}
az=${angleStringConverter.toString(az)}"""

                gc.fill = Styles.radarFgColor.deriveColor(
                    0.0,
                    1.0,
                    1.0,
                    displayParameters.targetLayerOpacity * selectedTargetOpacityFactor
                )
                gc.stroke = color

                when (type) {
                    MovingTargetType.Cloud1 -> {
                        val bounds = combinedTransform.transform(BoundingBox(
                            plotPosCart.x - cloudOneImage.width / 2,
                            plotPosCart.y - cloudOneImage.height / 2,
                            cloudOneImage.width,
                            cloudOneImage.height
                        ))
                        gc.strokeRect(
                            pt.x - bounds.width / 2.0,
                            pt.y - bounds.height / 2.0,
                            bounds.width,
                            bounds.height
                        )
                        gc.drawImage(
                            cloudOneImage,
                            pt.x - bounds.width / 2.0,
                            pt.y - bounds.height / 2.0,
                            bounds.width,
                            bounds.height
                        )
                        gc.textBaseline = VPos.TOP
                        gc.fillText(
                            text,
                            pt.x,
                            pt.y + bounds.height / 2 + 1.5 * Font.getDefault().size
                        )
                    }

                    MovingTargetType.Cloud2 -> {
                        val bounds = combinedTransform.transform(BoundingBox(
                            plotPosCart.x - cloudTwoImage.width / 2,
                            plotPosCart.y - cloudTwoImage.height / 2,
                            cloudTwoImage.width,
                            cloudTwoImage.height
                        ))
                        gc.strokeRect(
                            pt.x - bounds.width / 2.0,
                            pt.y - bounds.height / 2.0,
                            bounds.width,
                            bounds.height
                        )
                        gc.drawImage(
                            cloudTwoImage,
                            pt.x - bounds.width / 2.0,
                            pt.y - bounds.height / 2.0,
                            bounds.width,
                            bounds.height
                        )
                        gc.textBaseline = VPos.TOP
                        gc.fill = Styles.movingTargetPositionLabelColor.deriveColor(
                            0.0,
                            1.0,
                            1.0,
                            displayParameters.targetLayerOpacity * selectedTargetOpacityFactor
                        )
                        gc.fillText(
                            text,
                            pt.x,
                            pt.y + bounds.height / 2 + 1.5 * Font.getDefault().size
                        )
                    }

                    MovingTargetType.Point -> {
                        gc.strokeRect(
                            pt.x - 10.0 / 2.0,
                            pt.y - 10.0 / 2.0,
                            10.0,
                            10.0
                        )
                        gc.textBaseline = VPos.TOP
                        gc.fill = Styles.movingTargetPositionLabelColor.deriveColor(
                            0.0,
                            1.0,
                            1.0,
                            displayParameters.targetLayerOpacity * selectedTargetOpacityFactor
                        )
                        gc.fillText(
                            text,
                            pt.x,
                            pt.y + 10.0 / 2 + 1.5 * Font.getDefault().size
                        )
                    }

                    MovingTargetType.Test1 -> {
                        gc.strokeRect(
                            pt.x - 10.0 / 2.0,
                            pt.y - 10.0 / 2.0,
                            10.0,
                            10.0
                        )
                        gc.textBaseline = VPos.TOP
                        gc.fill = Styles.movingTargetPositionLabelColor.deriveColor(
                            0.0,
                            1.0,
                            1.0,
                            displayParameters.targetLayerOpacity * selectedTargetOpacityFactor
                        )
                        gc.fillText(
                            text,
                            pt.x,
                            pt.y + 10.0 / 2 + 1.5 * Font.getDefault().size
                        )
                    }

                    MovingTargetType.Test2 -> {
                        gc.strokeRect(
                            pt.x - 10.0 / 2.0,
                            pt.y - 10.0 / 2.0,
                            10.0,
                            10.0
                        )
                        gc.textBaseline = VPos.TOP
                        gc.fill = Styles.movingTargetPositionLabelColor.deriveColor(
                            0.0,
                            1.0,
                            1.0,
                            displayParameters.targetLayerOpacity * selectedTargetOpacityFactor
                        )
                        gc.fillText(
                            text,
                            pt.x,
                            pt.y + 10.0 / 2 + 1.5 * Font.getDefault().size
                        )
                    }
                }

            }

    }

    var lruHits = LRUMap<Int, List<Pair<Int, Int>>>(10)

    fun drawTargetHits(gc: GraphicsContext) {

        val alpha = gc.globalAlpha
        gc.globalAlpha = displayParameters.clutterLayerOpacity

        val radarParameters = simulatorController.radarParameters
        val cParams = CalculationParameters(radarParameters)

        val n = 6

        val currentTimeSec = simulatedCurrentTimeSecProperty.get()
        val currentArpIdx = floor(currentTimeSec / radarParameters.seekTimeSec).toInt()

        val fromArpIdx = max(
            0,
            (currentArpIdx - n)
        )

        // merge current ARP till the current time
        val fromCurrArpSec = floor(currentTimeSec / radarParameters.seekTimeSec) * radarParameters.seekTimeSec
        if (currentTimeSec > fromCurrArpSec) {
            log.finer { "Calculate current hits for range $fromCurrArpSec to $currentTimeSec" }
            designerController.calculateTargetHits(fromCurrArpSec, currentTimeSec)
                .forEach { drawRadarHitImage(gc, it, cParams, combinedTransform) }
        }

        // merge history
        IntStream.range(fromArpIdx, currentArpIdx).forEach { arpIdx ->

            lruHits.computeIfAbsent(arpIdx, { arpIdx ->

                val fromTimeSec = arpIdx * radarParameters.seekTimeSec
                val toTimeSec = (arpIdx + 1) * radarParameters.seekTimeSec

                log.finer { "Calculate hits for range $fromTimeSec to $toTimeSec" }
                designerController.calculateTargetHits(fromTimeSec, toTimeSec)
                    .distinct()
                    .collect(Collectors.toList())
            }).forEach { drawRadarHitImage(gc, it, cParams, combinedTransform) }
        }

        gc.globalAlpha = alpha

    }

}
