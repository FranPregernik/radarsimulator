package hr.franp.rsim

import hr.franp.rsim.models.*
import hr.franp.rsim.shapes.*
import javafx.animation.*
import javafx.beans.property.*
import javafx.beans.value.*
import javafx.event.*
import javafx.geometry.*
import javafx.scene.*
import javafx.scene.image.*
import javafx.scene.layout.*
import javafx.scene.paint.*
import javafx.scene.shape.*
import javafx.scene.transform.*
import javafx.util.*
import tornadofx.*
import java.lang.Math.*
import java.util.*
import java.util.Spliterators.*
import java.util.stream.StreamSupport.*

class RadarScreenView : View() {

    /**
     * Current time in the simulation
     */
    val simulatedCurrentTimeSecProperty = SimpleDoubleProperty(0.0)

    var displayParameters by property(readConfig())

    val displayParametersProperty = getProperty(RadarScreenView::displayParameters)

    private var combinedTransform: Transform = Affine()
    private var invCombinedTransform: Transform = combinedTransform.createInverse()

    val cloudOneImage = processHitMaskImage(Image(resources["/cloud1.png"]))
    val cloudTwoImage = processHitMaskImage(Image(resources["/cloud2.png"]))

    val mousePositionProperty = SimpleObjectProperty<RadarCoordinate>()
    val mouseClickProperty = SimpleObjectProperty<RadarCoordinate>()

    val selectionRect = Rectangle().apply {
        fill = Color.DARKGRAY.deriveColor(3.0, 1.0, 1.0, 0.4)
    }

    val movingHitsLayerOpacityProperty = SimpleDoubleProperty(1.0)
    val stationaryTargetLayerOpacityProperty = SimpleDoubleProperty(1.0)
    val movingTargetsLayerOpacityProperty = SimpleDoubleProperty(1.0)

    override val root = Pane().apply {
        addClass(Styles.radarScreen)
    }

    val boundsChangeListener = ChangeListener<Number> { _, _, _ ->
        root.clip = Rectangle(0.0, 0.0, root.width, root.height)
        draw()
    }

    private val designerController: DesignerController by inject()
    private val simulatorController: SimulatorController by inject()

    private val staticMarkersGroup = Pane()
    private val dynamicMarkersGroup = Pane()
    private val movingTargetsGroup = Pane().apply {
        opacityProperty().bind(movingTargetsLayerOpacityProperty)
    }

    private val stationaryTargetsGroup = Pane().apply {
        opacityProperty().bind(stationaryTargetLayerOpacityProperty)
    }
    private val hitsGroup = Pane()

    private val angleStringConverter = AngleStringConverter()
    private val speedStringConverter = SpeedStringConverter()
    private val distanceStringConverter = DistanceStringConverter()

    init {

        with(root) {

            setOnMouseMoved {
                val displayPoint = invCombinedTransform.transform(it.x, it.y)
                mousePositionProperty.set(RadarCoordinate.fromCartesian(displayPoint.x, displayPoint.y))
            }

            setOnMousePressed {
                val displayPoint = invCombinedTransform.transform(it.x, it.y)
                mouseClickProperty.set(RadarCoordinate.fromCartesian(displayPoint.x, displayPoint.y))
            }

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

            this += staticMarkersGroup
            this += dynamicMarkersGroup
            this += stationaryTargetsGroup
            this += hitsGroup
            // draw shapes on top of hits
            this += movingTargetsGroup
        }

        // Redraw canvas when size changes.
        root.widthProperty().addListener(boundsChangeListener)
        root.heightProperty().addListener(boundsChangeListener)

        // redraw after model changes
        simulatedCurrentTimeSecProperty.addListener { _, _, _ ->
            draw()
        }
        designerController.selectedMovingTargetProperty.addListener { _, _, _ ->
            drawMovingTargets()
        }
        displayParametersProperty.addListener { _, _, _ ->
            draw()
        }
        designerController.scenarioProperty.addListener { _, _, _ ->
            draw()
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
        targetDisplayFilter = emptySequence()
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
                t2Us = currentTimeUs,
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
        displayParametersProperty.set(
            displayParameters.copy(
                distanceUnit = distanceUnit,
                distanceStep = step ?: displayParameters.distanceStep
            )
        )
    }

    fun configAzimuthDisplay(azimuthMarkerType: AzimuthMarkerType, step: Int? = 0) {
        displayParametersProperty.set(
            displayParameters.copy(
                azimuthMarkerType = azimuthMarkerType,
                azimuthSteps = step ?: displayParameters.azimuthSteps
            )
        )
    }

    fun draw() {
        setupViewPort()
        drawStationaryTargets()
        drawStaticMarkers()
        drawDynamicMarkers()
        drawMovingTargets()
    }

    fun drawStaticMarkers() {
        staticMarkersGroup.children.clear()

        // draw distance markers
        val distanceMarkersGroup = Group()
        staticMarkersGroup.add(distanceMarkersGroup)

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
        val distanceSequence = mandatoryDistanceMarkers + generateSequence(simulatorController.radarParameters.minRadarDistanceKm) {
            it + stepKm
        }.takeWhile { it <= simulatorController.radarParameters.maxRadarDistanceKm - stepKm / 3 }

        val cp = combinedTransform.transform(0.0, 0.0)
        for (r in distanceSequence) {
            val dp = combinedTransform.transform(r, 0.0)
            val dist = cp.distance(dp)
            distanceMarkersGroup.add(DistanceMarkerCircle(cp, dist))
            distanceMarkersGroup.add(DistanceMarkerLabel(dp, (r / distanceToKmScale).toInt().toString()))
        }

        // draw angle markers
        val angleMarkersGroup = Group()
        staticMarkersGroup.add(angleMarkersGroup)

        val angleSequence: Sequence<Double>
        if (displayParameters.azimuthSteps >= 0) {
            val deltaAngleStep = TWO_PI / displayParameters.azimuthSteps
            angleSequence = generateSequence(0.0) { it + deltaAngleStep }
                .takeWhile { it < (TWO_PI - deltaAngleStep / 3) }
        } else {
            angleSequence = sequenceOf(0.0, HALF_PI, PI, 3 * HALF_PI, TWO_PI)
        }
        if (displayParameters.azimuthMarkerType == AzimuthMarkerType.FULL) {
            // draw full lines (like a spiderweb)
            for (a in angleSequence) {
                val p1 = combinedTransform.transform(
                    simulatorController.radarParameters.minRadarDistanceKm * Math.cos(a),
                    simulatorController.radarParameters.minRadarDistanceKm * Math.sin(a)
                )
                val p2 = combinedTransform.transform(
                    simulatorController.radarParameters.maxRadarDistanceKm * Math.cos(a),
                    simulatorController.radarParameters.maxRadarDistanceKm * Math.sin(a)
                )
                angleMarkersGroup.add(AzimuthMarkerLine(p1, p2))
            }
        } else {
            // draw only ticks on distance markers (circles)
            for (a in angleSequence) {
                for (d in distanceSequence.filter { it > simulatorController.radarParameters.minRadarDistanceKm }) {
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
                    angleMarkersGroup.add(AzimuthMarkerLine(p1, p2))
                }
            }
        }

        for (a in angleSequence) {
            val angle = HALF_PI - angleToAzimuth(a)
            val p = combinedTransform
                .transform(
                    simulatorController.radarParameters.maxRadarDistanceKm * cos(angle),
                    simulatorController.radarParameters.maxRadarDistanceKm * sin(angle)
                )
                .add(
                    20 * cos(angle),
                    -20 * sin(angle)
                )

            angleMarkersGroup.add(AzimuthMarkerLabel(p, a))
        }
    }

    fun drawDynamicMarkers() {
        dynamicMarkersGroup.children.clear()

        val rad = TWO_PI * (simulatedCurrentTimeSecProperty.get() / simulatorController.radarParameters.seekTimeSec)

        // draw simulation position markers
        val simPosGroup = Group()
        dynamicMarkersGroup.add(simPosGroup)

        val center = combinedTransform.transform(0.0, 0.0)
        val p = combinedTransform.transform(0.0, simulatorController.radarParameters.maxRadarDistanceKm)
        simPosGroup.add(Circle(p.x, p.y, 5.0, Color.RED))

        val deg = toDegrees(rad)
        val rotTransform = Rotate(deg, center.x, center.y)
        simPosGroup.transforms.setAll(rotTransform)

        if (simulatorController.simulationRunningProperty.get()) {
            timeline {
                keyframe(Duration.seconds(simulatorController.radarParameters.seekTimeSec)) {
                    keyvalue(rotTransform.angleProperty(), deg + 360, Interpolator.LINEAR)
                }
            }.apply {
                simulatorController.simulationRunningProperty.addListener { _, _, newValue ->
                    if (!newValue) {
                        stop()
                    }
                }
            }
        }

    }

    fun drawStationaryTargets() {

        stationaryTargetsGroup.children.clear()
        if (designerController.scenario.clutter == null) {
            return
        }

        // draw stationary targets
        val width = (2.0 * simulatorController.radarParameters.maxRadarDistanceKm)
        val height = (2.0 * simulatorController.radarParameters.maxRadarDistanceKm)

        val rasterMapImage = designerController.scenario.clutter.getImage(width.toInt(), height.toInt())

        val transformedBounds = combinedTransform.transform(BoundingBox(
            -width / 2.0,
            -height / 2.0,
            width,
            height
        ))

        stationaryTargetsGroup += imageview {
            image = rasterMapImage
            x = transformedBounds.minX
            y = transformedBounds.minY
            isPreserveRatio = true
            fitWidth = transformedBounds.width
            fitHeight = transformedBounds.height
        }

    }

    fun drawMovingTargets() {

        movingTargetsGroup.children.clear()

        val nonSelectedTargetsGroup = Group()
        movingTargetsGroup.add(nonSelectedTargetsGroup)

        val selectedTargetGroup = Group()
        movingTargetsGroup.add(selectedTargetGroup)

        val movingHitsGroup = Group().apply {
            opacityProperty().bind(movingHitsLayerOpacityProperty)
        }
        hitsGroup.children.setAll(movingHitsGroup)

        val currentTimeSec = simulatedCurrentTimeSecProperty.get()
        val currentTimeUs = S_TO_US * currentTimeSec

        // draw moving targets
        val noneSelected = designerController.selectedMovingTarget !in designerController.scenario.movingTargets
        val movingTargetCount = designerController.scenario.movingTargets.size
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

                // shift colors for each target and if the target is selected display other targets semi-transparent
                val color = Color.RED.deriveColor(
                    i * (360.0 / movingTargetCount),
                    1.0,
                    1.0,
                    1.0
                )

                val group = if (target != designerController.selectedMovingTarget) nonSelectedTargetsGroup else selectedTargetGroup
                group.add(
                    MovingTargetPathMarker(p).apply {
                        stroke = color
                    }
                )

                // draw segments and point markers
                var pFrom = combinedTransform.transform(
                    initPosCart.x,
                    initPosCart.y
                )
                target.directions.forEach { d ->
                    val dpc = combinedTransform.transform(d.destination.toCartesian())
                    group.add(
                        MovingTargetCourseLine(pFrom, dpc).apply {
                            stroke = color
                        }
                    )
                    group.add(
                        MovingTargetPathMarker(dpc).apply {
                            stroke = color
                        }
                    )
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

                val movingTarget = when (type) {
                    MovingTargetType.Cloud1 -> {
                        val bounds = combinedTransform.transform(BoundingBox(
                            plotPosCart.x - cloudOneImage.width / 2,
                            plotPosCart.y - cloudOneImage.height / 2,
                            cloudOneImage.width,
                            cloudOneImage.height
                        ))
                        MovingTargetPositionMarker(
                            p = pt,
                            text = text,
                            color = color,
                            image = cloudOneImage,
                            imageBounds = bounds
                        )
                    }
                    MovingTargetType.Cloud2 -> {
                        val bounds = combinedTransform.transform(BoundingBox(
                            plotPosCart.x - cloudTwoImage.width / 2,
                            plotPosCart.y - cloudTwoImage.height / 2,
                            cloudTwoImage.width,
                            cloudTwoImage.height
                        ))
                        MovingTargetPositionMarker(
                            p = pt,
                            text = text,
                            color = color,
                            image = cloudTwoImage,
                            imageBounds = bounds
                        )
                    }
                    MovingTargetType.Point -> MovingTargetPositionMarker(
                        p = pt,
                        text = text,
                        color = color
                    )
                    MovingTargetType.Test1 -> MovingTargetPositionMarker(
                        p = pt,
                        text = text,
                        color = color
                    )
                    MovingTargetType.Test2 -> MovingTargetPositionMarker(
                        p = pt,
                        text = text,
                        color = color
                    )
                }
                group.add(movingTarget)

            }

        // TEMP: draw last N plots relative to current time
        // HACK: not real plot points ....
        val cp = combinedTransform.transform(0.0, 0.0)
        val bp = combinedTransform.transform(simulatorController.radarParameters.maxRadarDistanceKm, 0.0)
        val horizontalAngleBeamWidthRad = toRadians(simulatorController.radarParameters.horizontalAngleBeamWidthDeg)
        val seekTimeUs = S_TO_US * simulatorController.radarParameters.seekTimeSec
        designerController.scenario.movingTargets
            .sortedBy { it.name }
            // show only if the target is selected or marked as display
            .filter { it == designerController.selectedMovingTarget || displayParameters.targetDisplayFilter.none() || it.name in displayParameters.targetDisplayFilter }
            .forEach { target ->
                val type = target.type ?: return@forEach


                val n = 6
                val fromTimeUs = max(
                    0.0,
                    seekTimeUs * (round(currentTimeSec / simulatorController.radarParameters.seekTimeSec) - n)
                )

                val timeIterator = generateSequence(fromTimeUs) { t -> t + designerController.scenario.simulationStepUs }
                    .takeWhile { t -> t <= currentTimeUs }
                    .iterator()

                stream(spliteratorUnknownSize(timeIterator, Spliterator.ORDERED), false)
                    .forEach inner@ { tUs ->

                        val plotPathSegment = getCurrentPathSegment(target, tUs)
                        val plotPos = plotPathSegment?.getPositionForTime(tUs) ?: return@inner
                        val plotPosCart = plotPos.toCartesian()

                        val sweepHeadingRad = TWO_PI / seekTimeUs * tUs
                        val targetHeadingRad = toRadians(plotPos.azDeg)
                        val diff = abs(((abs(targetHeadingRad - sweepHeadingRad) + PI) % TWO_PI) - PI)
                        if (diff > horizontalAngleBeamWidthRad / 2.0) {
                            return@inner
                        }

                        // range check
                        val distance = sqrt(pow(plotPosCart.x, 2.0) + pow(plotPosCart.y, 2.0))
                        if (distance < simulatorController.radarParameters.minRadarDistanceKm || distance > simulatorController.radarParameters.maxRadarDistanceKm) {
                            return@inner
                        }

                        val transformedPlot = combinedTransform.transform(plotPosCart)

                        val movingTarget = when (type) {
                            MovingTargetType.Cloud1 -> null
                            MovingTargetType.Cloud2 -> null
                            MovingTargetType.Point -> MovingTargetPlotMarker(
                                x = transformedPlot.x,
                                y = transformedPlot.y
                            )
                            MovingTargetType.Test1 -> Test1TargetHitMarker(cp, transformedPlot.distance(cp))
                            MovingTargetType.Test2 -> Test2TargetHitMarker(
                                cp,
                                plotPos.azDeg,
                                maxDistance = bp.distance(cp),
                                angleResolutionDeg = simulatorController.radarParameters.horizontalAngleBeamWidthDeg
                            )

                        }
                        if (movingTarget != null) {
                            movingHitsGroup.add(movingTarget)
                        }

                    }
            }

        // fade non selected targets
        if (!noneSelected) {
            nonSelectedTargetsGroup.opacity = 0.3
            selectedTargetGroup.opacity = 1.0
        }

        selectedTargetGroup.children
            .union(nonSelectedTargetsGroup.children)
            .filter { it is MovingTargetPathMarker || it is MovingTargetPositionMarker }
            .forEach(Node::toFront)
    }

}
