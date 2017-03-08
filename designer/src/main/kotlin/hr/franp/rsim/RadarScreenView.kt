package hr.franp.rsim

import hr.franp.rsim.models.*
import hr.franp.rsim.shapes.*
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
import tornadofx.*
import java.lang.Math.*
import java.util.*
import java.util.Spliterators.*
import java.util.stream.StreamSupport.*

class RadarScreenView : View() {

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

    private val controller: DesignerController by inject()

    private val staticMarkersGroup = Pane()
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
                    if (selectionRect.width == 0.0 && selectionRect.height == 0.0) {
                        controller.displayParameters.viewPort = null
                    } else {
                        controller.displayParameters.viewPort = invCombinedTransform.transform(selectionRect.boundsInLocal)
                    }

                    // refresh screen
                    draw()
                }
            })

            this += staticMarkersGroup
            this += stationaryTargetsGroup
            this += hitsGroup
            // draw shapes on top of hits
            this += movingTargetsGroup
        }

        // Redraw canvas when size changes.
        root.widthProperty().addListener(boundsChangeListener)
        root.heightProperty().addListener(boundsChangeListener)

        // redraw after model changes
        controller.displayParameters.simulatedCurrentTimeSecProperty().addListener { _, _, _ ->
            drawMovingTargets()
        }
        controller.selectedMovingTargetProperty.addListener { _, _, _ ->
            drawMovingTargets()
        }

    }


    private fun setupViewPort() {

        val viewPort = controller.displayParameters.viewPort ?: BoundingBox(
            -controller.radarParameters.maxRadarDistanceKm * 1.1,
            -controller.radarParameters.maxRadarDistanceKm * 1.1,
            2 * controller.radarParameters.maxRadarDistanceKm * 1.1,
            2 * controller.radarParameters.maxRadarDistanceKm * 1.1
        )

        val destViewPort = BoundingBox(0.0, 0.0, root.width, root.height)

        combinedTransform = setupViewPort(viewPort, destViewPort)
        invCombinedTransform = combinedTransform.createInverse()

    }

    private fun getCurrentPathSegment(movingTarget: MovingTarget, currentTimeUs: Double): PathSegment? {

        var p1 = movingTarget.initialPosition
        var t1 = 0.0

        movingTarget.directions.forEachIndexed { _, direction ->
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
                return PathSegment(
                    p1 = p1,
                    p2 = p2,
                    t1Us = t1,
                    t2Us = t1 + dt,
                    vxKmUs = speedKmUs * dx / distance,
                    vyKmUs = speedKmUs * dy / distance,
                    type = movingTarget.type
                )
            }

            p1 = p2
            t1 += dt

        }

        return null
    }

    fun draw() {
        setupViewPort()
        drawStationaryTargets()
        drawStaticMarkers()
        drawMovingTargets()
    }

    fun drawStaticMarkers() {
        staticMarkersGroup.children.clear()

        // draw distance markers
        val distanceMarkersGroup = Group()
        staticMarkersGroup.add(distanceMarkersGroup)

        // Distance markers
        val step = controller.displayParameters.distanceStepKm ?: controller.radarParameters.maxRadarDistanceKm
        val distanceSequence = sequenceOf(controller.radarParameters.minRadarDistanceKm, controller.radarParameters.maxRadarDistanceKm) +
            generateSequence(controller.radarParameters.minRadarDistanceKm) { it + step }
                .takeWhile { it <= controller.radarParameters.maxRadarDistanceKm - step / 3 }

        val cp = combinedTransform.transform(0.0, 0.0)
        for (r in distanceSequence) {
            val dp = combinedTransform.transform(r, 0.0)
            val dist = cp.distance(dp)
            distanceMarkersGroup.add(DistanceMarkerCircle(cp, dist))
            distanceMarkersGroup.add(DistanceMarkerLabel(dp, r.toInt().toString()))
        }

        // draw angle markers
        val angleMarkersGroup = Group()
        staticMarkersGroup.add(angleMarkersGroup)

        val angleSequence: Sequence<Double>
        if (controller.displayParameters.azimuthSteps >= 0) {
            val deltaAngleStep = TWO_PI / controller.displayParameters.azimuthSteps
            angleSequence = generateSequence(0.0) { it + deltaAngleStep }
                .takeWhile { it < (TWO_PI - deltaAngleStep / 3) }
        } else {
            angleSequence = sequenceOf(0.0, HALF_PI, Math.PI, 3 * HALF_PI, TWO_PI)
        }
        if (controller.displayParameters.azimuthMarkerType == AzimuthMarkerType.FULL) {
            // draw full lines (like a spiderweb)
            for (a in angleSequence) {
                val p1 = combinedTransform.transform(
                    controller.radarParameters.minRadarDistanceKm * Math.cos(a),
                    controller.radarParameters.minRadarDistanceKm * Math.sin(a)
                )
                val p2 = combinedTransform.transform(
                    controller.radarParameters.maxRadarDistanceKm * Math.cos(a),
                    controller.radarParameters.maxRadarDistanceKm * Math.sin(a)
                )
                angleMarkersGroup.add(AzimuthMarkerLine(p1, p2))
            }
        } else {
            // draw only ticks on distance markers (circles)
            for (a in angleSequence) {
                for (d in distanceSequence.filter { it > controller.radarParameters.minRadarDistanceKm }) {
                    val length = 5
                    val p1 = combinedTransform.transform(
                        (d - length / 2.0) * Math.cos(a),
                        (d - length / 2.0) * Math.sin(a)
                    )
                    val p2 = combinedTransform.transform(
                        (d + length / 2.0) * Math.cos(a),
                        (d + length / 2.0) * Math.sin(a)
                    )
                    angleMarkersGroup.add(AzimuthMarkerLine(p1, p2))
                }
            }
        }
        val angleTextRadius = controller.radarParameters.maxRadarDistanceKm + 20
        for (a in angleSequence) {
            val p = combinedTransform.transform(
                angleTextRadius * cos(angleToAzimuth(a) - HALF_PI),
                angleTextRadius * sin(angleToAzimuth(a) - HALF_PI)
            )
            angleMarkersGroup.add(AzimuthMarkerLabel(p, a))
        }

    }

    fun drawStationaryTargets() {

        stationaryTargetsGroup.children.clear()
        if (controller.scenario.clutter == null) {
            return
        }

        // draw stationary targets
        val width = (2.0 * controller.radarParameters.maxRadarDistanceKm)
        val height = (2.0 * controller.radarParameters.maxRadarDistanceKm)

        val rasterMapImage = controller.scenario.clutter.getImage(width.toInt(), height.toInt())

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

        val currentTimeS = controller.displayParameters.simulatedCurrentTimeSec ?: 0.0
        val currentTimeUs = S_TO_US * currentTimeS

        // draw moving targets
        val noneSelected = controller.selectedMovingTarget !in controller.scenario.movingTargets
        val movingTargetCount = controller.scenario.movingTargets.size
        controller.scenario.movingTargets
            .sortedBy { it.name }
            // show only if the target is selected or marked as display
            .filter { it == controller.selectedMovingTarget || controller.displayParameters.targetDisplayFilter.isEmpty() || it.name in controller.displayParameters.targetDisplayFilter }
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

                val group = if (target != controller.selectedMovingTarget) nonSelectedTargetsGroup else selectedTargetGroup
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

                val distance = sqrt(pow(pt.x, 2.0) + pow(pt.y, 2.0))
                val az = toDegrees(angleToAzimuth(atan2(pt.y, pt.x)))

                val text = """${target.name}
hdg=${angleStringConverter.toString(plotPathSegment.headingDeg)}
spd=${speedStringConverter.toString(plotPathSegment.vKmh)}
r=${distanceStringConverter.toString(distance)}
az=${angleStringConverter.toString(az)}"""

                val movingTarget = when (type) {
                    MovingTargetType.Cloud1 -> {
                        MovingTargetPositionMarker(
                            p = pt,
                            text = text,
                            width = 300.0,
                            height = 300.0,
                            color = color,
                            image = cloudOneImage
                        )
                    }
                    MovingTargetType.Cloud2 -> {
                        MovingTargetPositionMarker(
                            p = pt,
                            text = text,
                            width = 300.0,
                            height = 300.0,
                            color = color,
                            image = cloudTwoImage
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

        controller.scenario.movingTargets
            .sortedBy { it.name }
            // show only if the target is selected or marked as display
            .filter { it == controller.selectedMovingTarget || controller.displayParameters.targetDisplayFilter.isEmpty() || it.name in controller.displayParameters.targetDisplayFilter }
            .forEach { target ->
                val type = target.type ?: return@forEach

                // TEMP: draw last N plots relative to current time
                // HACK: not real plot points ....
                if (type == MovingTargetType.Point) {
                    val n = 6
                    val fromTimeUs = S_TO_US * controller.radarParameters.seekTimeSec * (floor(currentTimeS / controller.radarParameters.seekTimeSec) - n)

                    val timeIterator = generateSequence(fromTimeUs) { t -> t + S_TO_US * controller.radarParameters.seekTimeSec }
                        .takeWhile { t -> t < currentTimeUs }
                        .iterator()

                    stream(spliteratorUnknownSize(timeIterator, Spliterator.ORDERED), false)
                        .forEach inner@ { t ->

                            val plotPathSegment = getCurrentPathSegment(target, t)
                            val plotPosCart = plotPathSegment?.getPositionForTime(t)?.toCartesian()

                            if (plotPosCart != null) {

                                // range check
                                val distance = sqrt(pow(plotPosCart.x, 2.0) + pow(plotPosCart.y, 2.0))
                                if (distance < controller.radarParameters.minRadarDistanceKm || distance > controller.radarParameters.maxRadarDistanceKm) {
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
                                    MovingTargetType.Test1 -> Test1TargetHitMarker(
                                        x = transformedPlot.x,
                                        y = transformedPlot.y
                                    )
                                    MovingTargetType.Test2 -> Test2TargetHitMarker(
                                        x = transformedPlot.x,
                                        y = transformedPlot.y,
                                        maxDistance = controller.radarParameters.maxRadarDistanceKm,
                                        angleResolutionDeg = controller.radarParameters.horizontalAngleBeamWidthDeg
                                    )
                                }
                                if (movingTarget != null) {
                                    movingHitsGroup.add(movingTarget)
                                }
                            }
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
