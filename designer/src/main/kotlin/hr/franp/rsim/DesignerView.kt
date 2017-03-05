package hr.franp.rsim

import hr.franp.*
import hr.franp.rsim.models.*
import hr.franp.rsim.models.AzimuthMarkerType.*
import hr.franp.rsim.models.DistanceUnit.*
import javafx.beans.property.*
import javafx.embed.swing.*
import javafx.geometry.*
import javafx.scene.control.*
import javafx.scene.image.*
import javafx.scene.layout.*
import javafx.scene.text.*
import javafx.stage.*
import javafx.util.converter.*
import net.schmizz.sshj.*
import net.schmizz.sshj.common.*
import net.schmizz.sshj.xfer.*
import org.controlsfx.glyphfont.*
import org.controlsfx.glyphfont.FontAwesome.Glyph.*
import tornadofx.*
import java.io.*
import java.nio.*
import javax.imageio.*

class DesignerView : View() {
    override val root = BorderPane()
    val status: TaskStatus by inject()

    private var fontAwesome = GlyphFontRegistry.font("FontAwesome")
    private val controller: DesignerController by inject()
    private val radarScreen: RadarScreenView by inject()
    private val movingTargetEditor: MovingTargetEditorView by inject()
    private val movingTargetSelector: MovingTargetSelectorView by inject()

    val calculatingHitsProperty = SimpleBooleanProperty(false)

    init {

        with(root) {

            maxWidth = 500.0
            minWidth = 300.0

            // add and ensure radar screen fills the space
            center = radarScreen.root.apply {

            }

            right = vbox {

                /**
                 * Display of background task progress
                 */
                hbox(4.0) {

                    paddingAll = 4

                    button("CALC") {
                        disableProperty().bind(calculatingHitsProperty)

                        tooltip("Calculate and display all hits")

                        setOnAction {
                            calculatingHitsProperty.set(true)

                            try {
                                runAsync {

                                    FileOutputStream("clutter.bin").use { stream ->
                                        stream.write(
                                            ByteBuffer.allocate(4)
                                                .order(ByteOrder.LITTLE_ENDIAN)
                                                .putInt((controller.radarParameters.seekTimeSec * S_TO_US).toInt())
                                                .array()
                                        )
                                        stream.write(
                                            ByteBuffer.allocate(4)
                                                .order(ByteOrder.LITTLE_ENDIAN)
                                                .putInt(controller.radarParameters.azimuthChangePulse.toInt())
                                                .array()
                                        )
                                        stream.write(
                                            ByteBuffer.allocate(4)
                                                .order(ByteOrder.LITTLE_ENDIAN)
                                                .putInt(controller.radarParameters.impulsePeriodUs.toInt())
                                                .array()
                                        )
                                        stream.write(
                                            ByteBuffer.allocate(4)
                                                .order(ByteOrder.LITTLE_ENDIAN)
                                                .putInt(controller.radarParameters.maxImpulsePeriodUs.toInt())
                                                .array()
                                        )

                                        updateMessage("Writing clutter sim")
                                        updateProgress(0.0, 1.0)
                                        val clutterHits = controller.calculateClutterHits()
                                        clutterHits.writeTo(stream)

                                        // DEBUG
                                        ImageIO.write(
                                            SwingFXUtils.fromFXImage(generateRadarHitImage(clutterHits, controller.radarParameters), null),
                                            "png",
                                            File("clutter.png")
                                        )

                                        updateMessage("Wrote clutter sim")
                                        updateProgress(1.0, 1.0)

                                    }

                                    FileOutputStream("targets.bin").use { stream ->
                                        stream.write(
                                            ByteBuffer.allocate(4)
                                                .order(ByteOrder.LITTLE_ENDIAN)
                                                .putInt((controller.radarParameters.seekTimeSec * S_TO_US).toInt())
                                                .array()
                                        )
                                        stream.write(
                                            ByteBuffer.allocate(4)
                                                .order(ByteOrder.LITTLE_ENDIAN)
                                                .putInt(controller.radarParameters.azimuthChangePulse.toInt())
                                                .array()
                                        )
                                        stream.write(
                                            ByteBuffer.allocate(4)
                                                .order(ByteOrder.LITTLE_ENDIAN)
                                                .putInt(controller.radarParameters.impulsePeriodUs.toInt())
                                                .array()
                                        )
                                        stream.write(
                                            ByteBuffer.allocate(4)
                                                .order(ByteOrder.LITTLE_ENDIAN)
                                                .putInt(controller.radarParameters.maxImpulsePeriodUs.toInt())
                                                .array()
                                        )

                                        val mergedHits = Bits(0)

                                        updateMessage("Writing target sim")
                                        updateProgress(0.0, 1.0)

                                        var seekTime = 0.0
                                        controller.calculateTargetHits().forEach {
                                            seekTime += controller.radarParameters.seekTimeSec
                                            updateProgress(
                                                seekTime / (controller.scenario.simulationDurationMin * MIN_TO_S),
                                                1.0
                                            )
                                            mergedHits.or(it)
                                            it.writeTo(stream)
                                        }

                                        // DEBUG
                                        ImageIO.write(
                                            SwingFXUtils.fromFXImage(generateRadarHitImage(mergedHits, controller.radarParameters), null),
                                            "png",
                                            File("targets.png")
                                        )


                                        updateMessage("Wrote target sim")
                                        updateProgress(1.0, 1.0)

                                    }


                                } ui {
                                    calculatingHitsProperty.set(false)
                                }
                            } finally {
                                calculatingHitsProperty.set(false)
                            }

                        }
                    }

                    button("START") {
                        disableProperty().bind(calculatingHitsProperty)

                        tooltip("Transfer and begin simulation")

                        setOnAction {

                            runAsync {

                                SSHClient().apply {
                                    // no need to verify, not really security oriented
                                    addHostKeyVerifier { hostname, port, key -> true }

                                    useCompression()

                                    connect("192.168.0.108")

                                    // again security here is not an issue - petalinux default login
                                    authPassword("root", "root")

                                    newSCPFileTransfer().apply {
                                        transferListener = object : TransferListener {
                                            override fun directory(name: String?): TransferListener {
                                                return this
                                            }

                                            override fun file(name: String?, size: Long): StreamCopier.Listener {
                                                return StreamCopier.Listener { transferred ->
                                                    updateMessage("Transferring $name")
                                                    updateProgress(transferred, size)
                                                }
                                            }
                                        }
                                        upload(FileSystemFile("clutter.bin"), "/var/")
                                        upload(FileSystemFile("targets.bin"), "/var/")
                                    }

                                    startSession().use { session ->
                                        session.exec("killall radar-sim-test; radar-sim=test -r --load-clutter-file /var/clutter.bin --load-target-file /var/targets.bom").join()
                                    }
                                }

                            }

                        }
                    }

                    progressbar(status.progress).apply {
                        visibleWhen { status.running }
                    }

                    label(status.message).apply {
                        visibleWhen { status.running }
                    }

                }

                titledpane("Control", pane {

                    form {
                        fieldset {
                            field {
                                label("Distance") {
                                    tooltip("Distance marker display type")
                                }

                                togglegroup {
                                    togglebutton(Km.toString()) {
                                        isSelected = true
                                        setOnAction {
                                            controller.displayParameters.distanceUnit = Km
                                            radarScreen.drawStaticMarkers()
                                        }
                                    }
                                    togglebutton(NM.toString()) {
                                        setOnAction {
                                            controller.displayParameters.distanceUnit = NM
                                            radarScreen.drawStaticMarkers()
                                        }
                                    }
                                }

                                togglegroup {
                                    togglebutton("0").setOnAction {
                                        controller.displayParameters.distanceStepKm = 0.0
                                        radarScreen.drawStaticMarkers()
                                    }
                                    togglebutton("10").setOnAction {
                                        controller.displayParameters.distanceStepKm = 10.0
                                        radarScreen.drawStaticMarkers()
                                    }
                                    togglebutton("50") {
                                        isSelected = true
                                        setOnAction {
                                            controller.displayParameters.distanceStepKm = 50.0
                                            radarScreen.drawStaticMarkers()
                                        }
                                    }
                                }
                            }

                            field {

                                label("Azimuth") {
                                    tooltip("Angle marker display type")
                                }

                                togglegroup {
                                    togglebutton(FULL.toString()) {
                                        isSelected = true
                                        setOnAction {
                                            controller.displayParameters.azimuthMarkerType = FULL
                                            radarScreen.drawStaticMarkers()
                                        }
                                    }
                                    togglebutton(MIN.toString()) {
                                        setOnAction {
                                            controller.displayParameters.azimuthMarkerType = MIN
                                            radarScreen.drawStaticMarkers()
                                        }
                                    }
                                }

                                togglegroup {
                                    togglebutton("0").setOnAction {
                                        controller.displayParameters.azimuthSteps = 0
                                        radarScreen.drawStaticMarkers()
                                    }
                                    togglebutton("5").setOnAction {
                                        controller.displayParameters.azimuthSteps = 72
                                        radarScreen.drawStaticMarkers()
                                    }
                                    togglebutton("10") {
                                        isSelected = true
                                        setOnAction {
                                            controller.displayParameters.azimuthSteps = 36
                                            radarScreen.drawStaticMarkers()
                                        }
                                    }
                                }

                            }

                            field("Target layer") {

                                slider {
                                    tooltip("Controls transparency of the target layer")

                                    min = 0.0
                                    max = 1.0
                                    blockIncrement = 0.1
                                    bind(radarScreen.movingTargetsLayerOpacityProperty)
                                }
                            }

                            field("Target hit layer") {

                                slider {
                                    tooltip("Controls transparency of the target hit layer")

                                    min = 0.0
                                    max = 1.0
                                    blockIncrement = 0.1
                                    bind(radarScreen.movingHitsLayerOpacityProperty)
                                }
                            }

                            field("Clutter layer") {

                                slider {
                                    tooltip("Controls transparency of the clutter layer")

                                    min = 0.0
                                    max = 1.0
                                    blockIncrement = 0.1
                                    bind(radarScreen.stationaryTargetLayerOpacityProperty)
                                }
                            }

                            field("Clutter hit layer") {

                                slider {
                                    tooltip("Controls transparency of the clutter hit layer")

                                    min = 0.0
                                    max = 1.0
                                    blockIncrement = 0.1
                                    bind(radarScreen.stationaryHitsLayerOpacityProperty)
                                }
                            }

                            field("Clutter map") {
                                button("", fontAwesome.create(FILE_PHOTO_ALT)) {
                                    setOnAction {
                                        val files = chooseFile("Select clutter map", arrayOf(FileChooser.ExtensionFilter("Image  files", "*.jpg")))
                                        println("The user chose $files")
                                        if (files.isNotEmpty()) {
                                            controller.scenario.clutter = Clutter(files.first())
                                            radarScreen.drawStationaryTargets()
                                            this.tooltip = Tooltip("Select clutter map").apply {
                                                graphic = ImageView(controller.scenario.clutter.getImage(100, 100))
                                            }
                                        }
                                    }
                                }

                            }
                        }
                        fieldset {
                            field("Tsim") {

                                button("", fontAwesome.create(STEP_BACKWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(0.0))
                                    setOnAction {
                                        controller.displayParameters.simulatedCurrentTimeSec = 0.0
                                    }
                                }
                                button("", fontAwesome.create(FAST_BACKWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(0.0))
                                    minWidth = Font.getDefault().size * 3
                                    setOnAction {
                                        val simulatedCurrentTimeSec = controller.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec - 10 * controller.radarParameters.seekTimeSec
                                        controller.displayParameters.simulatedCurrentTimeSec = Math.max(newTime, 0.0)
                                    }
                                }
                                button("", fontAwesome.create(BACKWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(0.0))
                                    setOnAction {
                                        val simulatedCurrentTimeSec = controller.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec - controller.radarParameters.seekTimeSec
                                        controller.displayParameters.simulatedCurrentTimeSec = Math.max(newTime, 0.0)
                                    }
                                }

                                textfield {
                                    textProperty().bindBidirectional(controller.displayParameters.simulatedCurrentTimeSecProperty(), DoubleStringConverter())
                                    minWidth = Font.getDefault().size * 5
                                }

                                button("", fontAwesome.create(FORWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(controller.scenario.simulationDurationMin * MIN_TO_S))
                                    setOnAction {
                                        val simulatedCurrentTimeSec = controller.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec + controller.radarParameters.seekTimeSec
                                        controller.displayParameters.simulatedCurrentTimeSec = Math.min(newTime, controller.scenario.simulationDurationMin * MIN_TO_S)
                                    }
                                }
                                button("", fontAwesome.create(FAST_FORWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(controller.scenario.simulationDurationMin * MIN_TO_S))
                                    minWidth = Font.getDefault().size * 3
                                    setOnAction {
                                        val simulatedCurrentTimeSec = controller.displayParameters.simulatedCurrentTimeSec ?: 0.0
                                        val newTime = simulatedCurrentTimeSec + 10 * controller.radarParameters.seekTimeSec
                                        controller.displayParameters.simulatedCurrentTimeSec = Math.min(newTime, controller.scenario.simulationDurationMin * MIN_TO_S)
                                    }
                                }
                                button("", fontAwesome.create(STEP_FORWARD)) {
                                    disableProperty().bind(controller.displayParameters.simulatedCurrentTimeSecProperty().isEqualTo(controller.scenario.simulationDurationMin * MIN_TO_S))
                                    setOnAction {
                                        controller.displayParameters.simulatedCurrentTimeSec = controller.scenario.simulationDurationMin * MIN_TO_S
                                    }
                                }

                            }
                        }
                    }

                })

                titledpane("Moving targets", vbox {
                    padding = Insets.EMPTY
                    this += movingTargetSelector.root
                    this += movingTargetEditor.root
                    autosize()
                })

            }


        }

    }
}
