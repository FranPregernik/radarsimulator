package hr.franp.rsim

import hr.franp.rsim.models.*
import hr.franp.rsim.shapes.*
import javafx.beans.property.*
import javafx.beans.value.*
import javafx.event.*
import javafx.geometry.*
import javafx.scene.*
import javafx.scene.canvas.*
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

    val cloudOneImage = processHitMaskImage(Image(resources["/cloud1.png"]))
    val cloudTwoImage = processHitMaskImage(Image(resources["/cloud2.png"]))

    val mousePositionProperty = SimpleObjectProperty<RadarCoordinate>()
    val mouseClickProperty = SimpleObjectProperty<RadarCoordinate>()

    val selectionRect = Rectangle().apply {
        fill = Color.DARKGRAY.deriveColor(3.0, 1.0, 1.0, 0.4)
    }

    val stationaryHitsLayerOpacityProperty = SimpleDoubleProperty(1.0)
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
                val factor = 1.0 / getRadarScalingFactor()
                val displayPoint = staticMarkersGroup.parentToLocal(it.x, it.y)
                mousePositionProperty.set(RadarCoordinate.fromCartesian(factor * displayPoint.x, factor * displayPoint.y))
            }

            setOnMousePressed {
                val factor = 1.0 / getRadarScalingFactor()
                val displayPoint = staticMarkersGroup.parentToLocal(it.x, it.y)
                mouseClickProperty.set(RadarCoordinate.fromCartesian(factor * displayPoint.x, factor * displayPoint.y))
            }

            addSelectionRectangleGesture(this, selectionRect, EventHandler {
                if (it.isConsumed) {
                    if (selectionRect.width == 0.0 && selectionRect.height == 0.0) {
                        controller.displayParameters.viewPort = null
                    } else {
                        println(selectionRect.boundsInParent)
                        println(selectionRect.boundsInLocal)
                        println(staticMarkersGroup.parentToLocal(selectionRect.boundsInLocal))
                        controller.displayParameters.viewPort = staticMarkersGroup.parentToLocal(selectionRect.boundsInLocal)
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
        controller.displayParameters.simulatedCurrentTimeSecProperty().addListener { observableValue, oldTime, newTime ->
            drawMovingTargets()
        }
        controller.selectedMovingTargetProperty.addListener { observableValue, oldTime, newTime ->
            drawMovingTargets()
        }

    }

    private fun setupViewPort(node: Node): Double {

        val viewPort = controller.displayParameters.viewPort ?: BoundingBox(-root.width / 2, -root.height / 2, root.width, root.height)
        val destViewPort = BoundingBox(0.0, 0.0, root.width, root.height)

        val calcScale = max(destViewPort.width / viewPort.width, destViewPort.height / viewPort.height)
        val displayScale = if (calcScale.isNaN()) 1.0 else calcScale

        val centerDestX = (destViewPort.minX + destViewPort.maxX) / 2.0
        val centerDestY = (destViewPort.minY + destViewPort.maxY) / 2.0

        val centerViewX = (viewPort.minX + viewPort.maxX) / 2.0
        val centerViewY = (viewPort.minY + viewPort.maxY) / 2.0

        val dx = centerDestX - centerViewX
        val dy = centerDestY - centerViewY

        // reset
        if (node is Canvas) {
            val transform = Affine()

            // translate so viewport is centered in screen
            transform.append(Translate(dx, dy))

            // invert Y axis so coordinate system is math/geometry like
            transform.append(Scale(displayScale, -displayScale))

            node.graphicsContext2D.transform = transform

        } else {

            node.transforms.clear()

            // translate so viewport is centered in screen
            node.transforms.add(Translate(dx, dy))

            // invert Y axis so coordinate system is math/geometry like
            node.transforms.add(Scale(displayScale, -displayScale))
        }

        println("Translate with scale $displayScale, and translate [$dx, $dy] - screen center [${destViewPort.width / 2}, ${destViewPort.height / 2}]")
        println(Point2D(centerViewX, centerViewY))
        println(node.localToParent(centerViewX, centerViewY))

        return displayScale
    }

    private fun getRadarDrawRadius(): Double {
        val maxRadius = Math.min(root.width, root.height) / 2
        val radius = 0.9 * maxRadius
        return radius
    }

    private fun getRadarScalingFactor(): Double {
        val radius = getRadarDrawRadius()

        // helper calculated constants
        return radius / controller.radarParameters.maxRadarDistanceKm
    }

    private fun getCurrentPathSegment(movingTarget: MovingTarget, currentTimeUs: Double): PathSegment? {

        var p1 = movingTarget.initialPosition
        var t1 = 0.0

        movingTarget.directions.forEachIndexed { i, direction ->
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
        drawStationaryTargets()
        drawStaticMarkers()
        drawMovingTargets()
    }

    fun drawStaticMarkers() {
        staticMarkersGroup.children.clear()

        // helper calculated constants
        val factor = getRadarScalingFactor()
        val radius = getRadarDrawRadius()

        // zoom scale factor compensation
        val displayScale = setupViewPort(staticMarkersGroup)

        // draw distance markers
        val distanceMarkersGroup = Group()
        staticMarkersGroup.add(distanceMarkersGroup)

        // Distance markers
        val distanceSequence = mutableSetOf(controller.radarParameters.minRadarDistanceKm, controller.radarParameters.maxRadarDistanceKm)
        if (controller.displayParameters.distanceStepKm > 0.0) {
            // add additional equidistant distance markers
            distanceSequence.addAll(
                generateSequence(0.0) { it + controller.displayParameters.distanceStepKm }
                    .dropWhile { it < controller.radarParameters.minRadarDistanceKm }
                    .takeWhile { it <= (controller.radarParameters.maxRadarDistanceKm - controller.displayParameters.distanceStepKm / 3.0) }
            )
        }
        for (r in distanceSequence) {
            distanceMarkersGroup.add(DistanceMarkerCircle(displayScale, factor * r))
            distanceMarkersGroup.add(DistanceMarkerLabel(displayScale, factor * r, r.toInt().toString()))
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
                val x1 = factor * controller.radarParameters.minRadarDistanceKm * Math.cos(a)
                val y1 = factor * controller.radarParameters.minRadarDistanceKm * Math.sin(a)
                val x2 = radius * Math.cos(a)
                val y2 = radius * Math.sin(a)
                angleMarkersGroup.add(AzimuthMarkerLine(displayScale, x1, y1, x2, y2))
            }
        } else {
            // draw only ticks on distance markers (circles)
            for (a in angleSequence) {
                for (d in distanceSequence.filter { it > controller.radarParameters.minRadarDistanceKm }) {
                    val length = 5
                    val x1 = factor * (d - length / 2.0) * Math.cos(a)
                    val y1 = factor * (d - length / 2.0) * Math.sin(a)
                    val x2 = factor * (d + length / 2.0) * Math.cos(a)
                    val y2 = factor * (d + length / 2.0) * Math.sin(a)
                    angleMarkersGroup.add(AzimuthMarkerLine(displayScale, x1, y1, x2, y2))
                }
            }
        }
        val angleTextRadius = radius + 20
        for (a in angleSequence) {
            val label = AzimuthMarkerLabel(displayScale, angleTextRadius, a)
            angleMarkersGroup.add(label)
        }

    }

    fun drawStationaryTargets() {

        stationaryTargetsGroup.children.clear()
        if (controller.scenario.clutter == null) {
            return
        }

        // helper calculated constants
        val factor = getRadarScalingFactor()
        if (factor <= 0.0) {
            return
        }

        // draw stationary targets
        val width = (factor * 2.0 * controller.radarParameters.maxRadarDistanceKm).toInt()
        val height = (factor * 2.0 * controller.radarParameters.maxRadarDistanceKm).toInt()
        val rasterMapImage = controller.scenario.clutter.getImage(width, height)

        stationaryTargetsGroup += imageview {
            image = rasterMapImage
            scaleY = -1.0 // invert inverted coordinate system to display the image right side up
            x = -rasterMapImage.width / 2.0
            y = -rasterMapImage.height / 2.0
        }


        setupViewPort(stationaryTargetsGroup)

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

        // helper calculated constants
        val factor = getRadarScalingFactor()

        // zoom scale factor compensation
        val displayScale = setupViewPort(movingTargetsGroup)
        setupViewPort(hitsGroup)

        // draw moving targets
        val noneSelected = controller.selectedMovingTarget !in controller.scenario.movingTargets
        val movingTargetCount = controller.scenario.movingTargets.size
        controller.scenario.movingTargets
            .sortedBy { it.name }
            // show only if the target is selected or marked as display
            .filter { it == controller.selectedMovingTarget || controller.displayParameters.targetDisplayFilter.isEmpty() || it.name in controller.displayParameters.targetDisplayFilter }
            .forEachIndexed { i, target ->

                val type = target.type ?: return@forEachIndexed

                // shift colors for each target and if the target is selected display other targets semi-transparent
                val color = Color.RED.deriveColor(
                    i * (360.0 / movingTargetCount),
                    1.0,
                    1.0,
                    1.0
                )

                val initPosCart = target.initialPosition.toCartesian()

                val group = if (target != controller.selectedMovingTarget) nonSelectedTargetsGroup else selectedTargetGroup

                group.add(
                    MovingTargetPathMarker(displayScale, factor * initPosCart.x, factor * initPosCart.y).apply {
                        stroke = color
                    }
                )

                // draw segments and point markers
                var x1 = initPosCart.x
                var y1 = initPosCart.y
                target.directions.forEach { d ->
                    val dpc = d.destination.toCartesian()
                    group.add(
                        MovingTargetCourseLine(displayScale, factor * x1, factor * y1, factor * dpc.x, factor * dpc.y).apply {
                            stroke = color
                        }
                    )
                    group.add(
                        MovingTargetPathMarker(displayScale, factor * dpc.x, factor * dpc.y).apply {
                            stroke = color
                        }
                    )
                    x1 = dpc.x
                    y1 = dpc.y
                }

                // draw current simulated position marker
                val currentTimeS = controller.displayParameters.simulatedCurrentTimeSec ?: 0.0
                val currentTimeUs = S_TO_US * currentTimeS
                val ps = getCurrentPathSegment(target, currentTimeUs)
                val pt = ps?.getPositionForTime(currentTimeUs)?.toCartesian()
                if (pt != null) {
                    val distance = sqrt(pow(pt.x, 2.0) + pow(pt.y, 2.0))
                    val az = toDegrees(angleToAzimuth(atan2(pt.y, pt.x)))

                    val text = """${target.name}
hdg=${angleStringConverter.toString(ps?.headingDeg)}
spd=${speedStringConverter.toString(ps?.vKmh)}
r=${distanceStringConverter.toString(distance)}
az=${angleStringConverter.toString(az)}"""

                    val movingTarget = when (type) {
                        MovingTargetType.Cloud1 -> {
                            MovingTargetPositionMarker(
                                displayScale = displayScale,
                                x = factor * pt.x,
                                y = factor * pt.y,
                                text = text,
                                width = 300.0,
                                height = 300.0,
                                color = color,
                                image = cloudOneImage
                            )
                        }
                        MovingTargetType.Cloud2 -> {
                            MovingTargetPositionMarker(
                                displayScale = displayScale,
                                x = factor * pt.x,
                                y = factor * pt.y,
                                text = text,
                                width = 300.0,
                                height = 300.0,
                                color = color,
                                image = cloudTwoImage
                            )
                        }
                        MovingTargetType.Point -> MovingTargetPositionMarker(
                            displayScale = displayScale,
                            x = factor * pt.x,
                            y = factor * pt.y,
                            text = text,
                            color = color
                        )
                        MovingTargetType.Test1 -> Test1TargetPositionMarker(
                            displayScale = displayScale,
                            x = factor * pt.x,
                            y = factor * pt.y,
                            text = text,
                            color = color
                        )
                        MovingTargetType.Test2 -> Test2TargetPositionMarker(
                            displayScale = displayScale,
                            x = factor * pt.x,
                            y = factor * pt.y,
                            text = text,
                            color = color,
                            maxDistance = factor * controller.radarParameters.maxRadarDistanceKm,
                            angleResolutionDeg = controller.radarParameters.horizontalAngleBeamWidthDeg
                        )
                    }
                    group.add(movingTarget)
                }


                // TEMP: draw last N plots relative to current time
                // HACK: not real plot points ....
                if (ps != null && type == MovingTargetType.Point) {
                    val n = 6
                    val fromTimeUs = S_TO_US * controller.radarParameters.seekTimeSec * (floor(currentTimeS / controller.radarParameters.seekTimeSec) - n)

                    val timeIterator = generateSequence(fromTimeUs) { t -> t + S_TO_US * controller.radarParameters.seekTimeSec }
                        .takeWhile { t -> t < currentTimeUs }
                        .iterator()

                    stream(spliteratorUnknownSize(timeIterator, Spliterator.ORDERED), false)
                        .forEach { t ->

                            val plotPathSegment = getCurrentPathSegment(target, t)
                            val plotPosCart = plotPathSegment?.getPositionForTime(t)?.toCartesian()

                            if (plotPosCart != null) {

                                // range check
                                val distance = sqrt(pow(plotPosCart.x, 2.0) + pow(plotPosCart.y, 2.0))
                                if (distance < controller.radarParameters.minRadarDistanceKm || distance > controller.radarParameters.maxRadarDistanceKm) {
                                    return@forEach
                                }

                                val movingTarget = MovingTargetPlotMarker(
                                    displayScale = displayScale,
                                    x = factor * plotPosCart.x,
                                    y = factor * plotPosCart.y
                                )
                                movingHitsGroup.add(movingTarget)
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
