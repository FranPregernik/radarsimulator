package hr.franp.rsim

import hr.franp.rsim.models.AzimuthMarkerType
import hr.franp.rsim.models.MovingTarget
import hr.franp.rsim.models.MovingTargetType
import hr.franp.rsim.models.PathSegment
import hr.franp.rsim.models.RadarCoordinate
import hr.franp.rsim.models.Scenario
import hr.franp.rsim.shapes.AzimuthMarkerLabel
import hr.franp.rsim.shapes.AzimuthMarkerLine
import hr.franp.rsim.shapes.DistanceMarkerCircle
import hr.franp.rsim.shapes.DistanceMarkerLabel
import hr.franp.rsim.shapes.MovingTargetCourseLine
import hr.franp.rsim.shapes.MovingTargetPathMarker
import hr.franp.rsim.shapes.MovingTargetPositionMarker
import hr.franp.rsim.shapes.Test1TargetPositionMarker
import hr.franp.rsim.shapes.Test2TargetPositionMarker
import hr.franp.rsim.shapes.movingHitMarker
import hr.franp.rsim.shapes.stationaryHitMarker
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.event.EventHandler
import javafx.geometry.BoundingBox
import javafx.geometry.Point2D
import javafx.scene.Group
import javafx.scene.Node
import javafx.scene.canvas.Canvas
import javafx.scene.image.Image
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Rectangle
import javafx.scene.transform.Affine
import javafx.scene.transform.Scale
import javafx.scene.transform.Translate
import tornadofx.View
import tornadofx.add
import tornadofx.addClass
import tornadofx.getProperty
import tornadofx.imageview
import tornadofx.plusAssign
import tornadofx.property
import java.lang.Math.PI
import java.lang.Math.abs
import java.lang.Math.atan2
import java.lang.Math.ceil
import java.lang.Math.floor
import java.lang.Math.max
import java.lang.Math.pow
import java.lang.Math.round
import java.lang.Math.sqrt
import java.lang.Math.toRadians
import java.util.*
import java.util.Spliterators.spliteratorUnknownSize
import java.util.stream.StreamSupport.stream


class RadarScreenView : View() {
    val mousePositionProperty = SimpleObjectProperty<RadarCoordinate>()
    val mouseClickProperty = SimpleObjectProperty<RadarCoordinate>()

    val selectionRect = Rectangle().apply {
        fill = Color.DARKGRAY.deriveColor(3.0, 1.0, 1.0, 0.4)
    }

    var stationaryHitsLayerOpacityProperty = SimpleDoubleProperty(1.0)
    var movingHitsLayerOpacityProperty = SimpleDoubleProperty(1.0)
    val stationaryTargetLayerOpacityProperty = SimpleDoubleProperty(1.0)
    val movingTargetsLayerOpacityProperty = SimpleDoubleProperty(1.0)

    var calculatingHits by property<Boolean>()
    fun calculatingHitsProperty() = getProperty(RadarScreenView::calculatingHits)

    override val root = Pane().apply {
        addClass(Styles.radarScreen)
    }

    val boundsChangeListener = ChangeListener<Number> { obs, oldVal, newVal ->
        root.clip = Rectangle(0.0, 0.0, root.width, root.height)
        draw()
    }

    private var movingHits by property<LinkedHashSet<Pair<Long, Long>>>(LinkedHashSet())
    private var stationaryHits by property<LinkedHashSet<Pair<Long, Long>>>(LinkedHashSet())

    private val controller: DesignerController by inject()

    private val staticMarkersGroup = Pane()
    private val movingTargetsGroup = Pane().apply {
        opacityProperty().bind(movingTargetsLayerOpacityProperty)
    }

    private val stationaryTargetsGroup = Pane().apply {
        opacityProperty().bind(stationaryTargetLayerOpacityProperty)
    }
    private val hitsGroup = Pane()

    init {

        with(root) {

            setOnMouseMoved {
                val factor = 1.0 / getRadarScalingFactor()
                val displayPoint = staticMarkersGroup.parentToLocal(it.x, it.y)
                mousePositionProperty.set(RadarCoordinate(factor * displayPoint.x, factor * displayPoint.y))
            }

            setOnMousePressed {
                val factor = 1.0 / getRadarScalingFactor()
                val displayPoint = staticMarkersGroup.parentToLocal(it.x, it.y)
                mouseClickProperty.set(RadarCoordinate(factor * displayPoint.x, factor * displayPoint.y))
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
        controller.displayParameters.viewPortProperty().addListener { observableValue, oldVal, newVal ->
            drawHits()
        }
        controller.displayParameters.simulatedCurrentTimeSecProperty().addListener { observableValue, oldTime, newTime ->
            drawMovingTargets()
            drawHits()
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
            val speedKmUs = direction.speedKmh / HOUR_US

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
        drawHits()
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
        if (controller.scenario.stationaryTargets == null) {
            return
        }

        // helper calculated constants
        val factor = getRadarScalingFactor()

        // draw stationary targets
        val width = (factor * 2.0 * controller.radarParameters.maxRadarDistanceKm).toInt()
        val height = (factor * 2.0 * controller.radarParameters.maxRadarDistanceKm).toInt()
        val rasterMapImage = controller.scenario.stationaryTargets.getImage(width, height) ?: return

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

        // helper calculated constants
        val factor = getRadarScalingFactor()

        // zoom scale factor compensation
        val displayScale = setupViewPort(movingTargetsGroup)

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

                val ipc = target.initialPosition.toCartesian()

                val group = if (target != controller.selectedMovingTarget) nonSelectedTargetsGroup else selectedTargetGroup

                group.add(
                    MovingTargetPathMarker(displayScale, factor * ipc.x, factor * ipc.y).apply {
                        stroke = color
                    }
                )

                // draw segments and point markers
                var x1 = ipc.x
                var y1 = ipc.y
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
                val currentTimeUs = S_TO_US * (controller.displayParameters.simulatedCurrentTimeSec ?: 0.0)
                val ps = getCurrentPathSegment(target, currentTimeUs)
                val pt = ps?.getPositionForTime(currentTimeUs)?.toCartesian()
                if (pt != null) {
                    val distance = sqrt(pow(pt.x, 2.0) + pow(pt.y, 2.0))
                    val text = "${target.name}\nhdg=${AngleStringConverter.decimalFormat.format(ps?.headingDeg)}\ns=${SpeedStringConverter.decimalFormat.format(ps?.vKmh)}\nr=${DistanceStringConverter.decimalFormat.format(distance)}"
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
                                image = processHitMaskImage(Image(resources["/cloud1.png"]))
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
                                image = processHitMaskImage(Image(resources["/cloud2.png"]))
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

    fun calculate() {
        runAsync {

            calculatingHits = true

            val stationaryHits = LinkedHashSet<Pair<Long, Long>>()
            val movingHits = LinkedHashSet<Pair<Long, Long>>()

            // (deep)clone for background processing
            val scenarioClone = controller.scenario.copy<Scenario>()
            val radarParameters = controller.radarParameters

            // calculate stationary target hits
            if (scenarioClone.stationaryTargets != null) {

                stationaryHits.addAll(calculateStationaryHits(scenarioClone))
            }

            val targetPathSegments = scenarioClone.movingTargets
                .flatMap { movingTarget ->
                    var p1 = movingTarget.initialPosition
                    var t1 = 0.0

                    movingTarget.directions.map { direction ->
                        val p2 = direction.destination
                        val speedKmUs = direction.speedKmh / HOUR_US

                        // distance from last course change point
                        val p1c = p1.toCartesian()
                        val p2c = p2.toCartesian()
                        val dx = p2c.x - p1c.x
                        val dy = p2c.y - p1c.y
                        val distance = sqrt(pow(dx, 2.0) + pow(dy, 2.0))
                        val dt = distance / speedKmUs


                        val pathSegment = PathSegment(
                            p1 = p1,
                            p2 = p2,
                            t1Us = t1,
                            t2Us = t1 + dt,
                            vxKmUs = speedKmUs * dx / distance,
                            vyKmUs = speedKmUs * dy / distance,
                            type = movingTarget.type
                        )

                        p1 = p2
                        t1 += dt

                        pathSegment
                    }
                }

            // calculate moving target hits for the complete simulation
            val simulationDurationUs = scenarioClone.simulationDurationMin * MIN_US
            val minTime = if (controller.displayParameters.simulatedCurrentTimeSec != null)
                S_TO_US * (controller.displayParameters.simulatedCurrentTimeSec - radarParameters.seekTimeSec)
            else 0.0
            val maxTime = if (controller.displayParameters.simulatedCurrentTimeSec != null)
                S_TO_US * controller.displayParameters.simulatedCurrentTimeSec
            else simulationDurationUs

            val timeIterator = generateSequence(minTime) { t -> t + scenarioClone.simulationStepUs }
                .takeWhile { t -> t < maxTime }
                .iterator()

            stream(spliteratorUnknownSize(timeIterator, Spliterator.ORDERED), false)
                .parallel()
                .forEach { t ->
                    targetPathSegments.forEach {
                        movingHits.addAll(
                            when (it.type) {
                                MovingTargetType.Point ->
                                    calculatePointTargetHits(it, t)
                                MovingTargetType.Cloud1 ->
                                    calculateCloudTargetHits(it, t)
                                MovingTargetType.Cloud2 ->
                                    calculateCloudTargetHits(it, t)
                                MovingTargetType.Test1 ->
                                    calculateTestTargetHits(it, t)
                                MovingTargetType.Test2 ->
                                    calculateTestTargetHits(it, t)
                            }
                        )
                    }
                }

            calculatingHits = false

            Pair(movingHits, stationaryHits)

        } ui {
            movingHits = it.first
            stationaryHits = it.second
            drawHits()
        }
    }

    private fun calculateTestTargetHits(pathSegment: PathSegment, t: Double): Collection<Pair<Long, Long>> {
        val hits = mutableListOf<Pair<Long, Long>>()

        return hits
    }

    private fun calculateCloudTargetHits(pathSegment: PathSegment, t: Double): Collection<Pair<Long, Long>> {
        val hits = mutableListOf<Pair<Long, Long>>()

        return hits
    }

    private fun calculateStationaryHits(scenario: Scenario): Collection<Pair<Long, Long>> {
        val hits = mutableListOf<Pair<Long, Long>>()

        val maxRadarDistanceKm = controller.radarParameters.maxRadarDistanceKm
        val minRadarDistanceKm = controller.radarParameters.minRadarDistanceKm
        val maxSignalTimeUs = ceil(maxRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)
        val minSignalTimeUs = ceil(minRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)
        val distanceResolutionKm = controller.radarParameters.distanceResolutionKm
        val azimuthChangePulseCount = controller.radarParameters.azimuthChangePulse
        val horizontalAngleBeamWidthRad = toRadians(controller.radarParameters.horizontalAngleBeamWidthDeg)
        val c1 = TWO_PI / azimuthChangePulseCount

        val width = round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()
        val height = round(2.0 * maxRadarDistanceKm / distanceResolutionKm).toInt()

        val raster = scenario.stationaryTargets.getRasterHitMap(width, height)

        for (hit in raster) {
            val dx = (hit.first - width / 2.0) * distanceResolutionKm
            val dy = (hit.second - height / 2.0) * distanceResolutionKm
            val radarDistanceKm = sqrt(pow(dx, 2.0) + pow(dy, 2.0))
            if (radarDistanceKm < minRadarDistanceKm || radarDistanceKm > maxRadarDistanceKm) {
                continue
            }

            val cartesianAngleRad = atan2(dy, dx)
            val sweepHeadingRad = angleToAzimuth(cartesianAngleRad)

            val minSweepIndex = floor((sweepHeadingRad - horizontalAngleBeamWidthRad) / c1).toLong()
            val maxSweepIndex = ceil((sweepHeadingRad + horizontalAngleBeamWidthRad) / c1).toLong()

            for (sweepIdx in minSweepIndex..maxSweepIndex) {
                val signalTimeUs = round(radarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)
                if (signalTimeUs > minSignalTimeUs && signalTimeUs < maxSignalTimeUs) {
                    hits.add(Pair(sweepIdx, signalTimeUs))
                }
            }
        }

        return hits
    }

    private fun calculatePointTargetHits(ps: PathSegment, t: Double): Collection<Pair<Long, Long>> {
        val hits = mutableListOf<Pair<Long, Long>>()

        val maxRadarDistanceKm = controller.radarParameters.maxRadarDistanceKm
        val minRadarDistanceKm = controller.radarParameters.minRadarDistanceKm
        val horizontalAngleBeamWidthRad = toRadians(controller.radarParameters.horizontalAngleBeamWidthDeg)
        val rotationTimeUs = controller.radarParameters.seekTimeSec * S_TO_US
        val sweepHeadingRad = TWO_PI / rotationTimeUs * t
        val azimuthChangePulseCount = controller.radarParameters.azimuthChangePulse
        val c1 = TWO_PI / azimuthChangePulseCount
        val maxSignalTimeUs = ceil(maxRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)
        val minSignalTimeUs = ceil(minRadarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)

        val pos = ps.getPositionForTime(t)
        val position = pos ?: return hits

        // get the angle of the target (center point)
        val radarDistanceKm = position.rKm
        if (radarDistanceKm < minRadarDistanceKm || radarDistanceKm > maxRadarDistanceKm) {
            return hits
        }

        val targetHeadingRad = toRadians(position.azDeg)
        val diff = abs(((abs(targetHeadingRad - sweepHeadingRad) + PI) % TWO_PI) - PI)
        if (diff > horizontalAngleBeamWidthRad / 2.0) {
            return hits
        }

        val sweepIdx = round(sweepHeadingRad / c1)
        val signalTimeUs = round(radarDistanceKm * LIGHTSPEED_US_TO_ROUNDTRIP_KM)
        if (signalTimeUs > minSignalTimeUs && signalTimeUs < maxSignalTimeUs) {
            // set signal hit
            hits.add(Pair(sweepIdx, signalTimeUs))
        }

        return hits
    }

    fun drawHits() {

        // helper calculated constants
        val factor = getRadarScalingFactor()
        val radarParameters = controller.radarParameters
        val c1 = TWO_PI / radarParameters.azimuthChangePulse
        val spreadRad = toRadians(radarParameters.horizontalAngleBeamWidthDeg)

        val movingHitsCanvas = Canvas(root.width, root.height).apply {
            opacityProperty().bind(movingHitsLayerOpacityProperty)
        }
        val stationaryHitsCanvas = Canvas(root.width, root.height).apply {
            opacityProperty().bind(stationaryHitsLayerOpacityProperty)
        }
        hitsGroup.children.setAll(movingHitsCanvas, stationaryHitsCanvas)

        // draw
        val movingHitsGrphCtx = movingHitsCanvas.graphicsContext2D
        val stationaryHitsGrphCtx = stationaryHitsCanvas.graphicsContext2D

        // zoom scale factor compensation
        setupViewPort(movingHitsCanvas)
        setupViewPort(stationaryHitsCanvas)

        for (i in movingHits) {
            val sweepIdx = i.first
            val signalTimeUs = i.second

            val sweepHeadingRad = sweepIdx * c1
            val distanceKm = signalTimeUs / LIGHTSPEED_US_TO_ROUNDTRIP_KM

            movingHitsGrphCtx.movingHitMarker(
                factor * distanceKm,
                factor * radarParameters.distanceResolutionKm,
                azimuthToAngle(sweepHeadingRad),
                spreadRad
            )
        }

        for (i in stationaryHits) {
            val sweepIdx = i.first
            val signalTimeUs = i.second

            val sweepHeadingRad = sweepIdx * c1
            val distanceKm = signalTimeUs / LIGHTSPEED_US_TO_ROUNDTRIP_KM

            stationaryHitsGrphCtx.stationaryHitMarker(
                factor * distanceKm,
                factor * radarParameters.distanceResolutionKm,
                azimuthToAngle(sweepHeadingRad),
                spreadRad
            )
        }

    }
}
