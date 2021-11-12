import javafx.application.Application
import javafx.application.Platform
import javafx.embed.swing.SwingFXUtils
import javafx.event.EventHandler
import javafx.scene.Scene
import javafx.scene.SnapshotParameters
import javafx.scene.canvas.Canvas
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.WritableImage
import javafx.scene.input.*
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import javafx.stage.Stage
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ScreenAndDraw : Application(){
    private var prevX = 0.0
    private var prevY = 0.0
    private val cfgName = "dir.cfg"
    private val defaultPath = System.getenv("USERPROFILE") + "\\Documents\\"

    override fun start(primaryStage: Stage) {
        // Инструменты
        val tools = HBox()

        val hideCheckbox = CheckBox("Hide")

        val delayVBox = VBox()
        val delayLabel = Label("Delay")
        val delaySlider = Slider(0.0, 5.0, 0.0)
        delaySlider.majorTickUnit = 1.0
        delaySlider.minorTickCount = 4
        delaySlider.isSnapToTicks = true
        delaySlider.isShowTickLabels = true
        delaySlider.isShowTickMarks = true
        delayVBox.children.addAll(delayLabel, delaySlider)

        val brushColorPicker = ColorPicker(Color.BLACK)

        val brushSizeVBox = VBox()
        val brushSizeLabel = Label("Brush size")
        val brushSizeSlider = Slider(1.0, 96.0, 12.0)
        brushSizeSlider.majorTickUnit = 16.0
        brushSizeSlider.minorTickCount = 7
        brushSizeSlider.isSnapToTicks = true
        brushSizeSlider.isShowTickLabels = true
        brushSizeSlider.isShowTickMarks = true
        brushSizeVBox.children.addAll(brushSizeLabel, brushSizeSlider)

        val cutModeCheckBox = CheckBox("Cut")

        // Полотно
        val scrollPane = ScrollPane()
        scrollPane.isFitToHeight = true
        scrollPane.isFitToWidth = true
        val stackPane = StackPane()
        val imgCanvas = Canvas(stackPane.width, stackPane.height)
        val drawCanvas = Canvas(stackPane.width, stackPane.height)
        val cutCanvas = Canvas(stackPane.width, stackPane.height)
        stackPane.children.addAll(imgCanvas, drawCanvas, cutCanvas)
        scrollPane.content = stackPane
        val drawCtx = drawCanvas.graphicsContext2D
        val cutCtx = cutCanvas.graphicsContext2D

        cutCanvas.onMousePressed = EventHandler { e ->
            if(cutModeCheckBox.isSelected) {
                prevX = e.x
                prevY = e.y
                cutCtx.fill = Color.rgb(0, 0, 0, 0.5)
                cutCtx.fillRect(0.0, 0.0, drawCanvas.width, drawCanvas.height)
            }
            else {
                val size = brushSizeSlider.value
                val x = e.x - size / 2
                val y = e.y - size / 2
                if (e.button == MouseButton.SECONDARY) {
                    drawCtx.clearRect(x, y, size, size)
                } else {
                    drawCtx.fill = brushColorPicker.value
                    drawCtx.fillOval(x, y, size, size)
                }
            }
        }

        cutCanvas.onMouseDragged = EventHandler { e ->
            if(cutModeCheckBox.isSelected) {
                cutCtx.clearRect(0.0, 0.0, drawCanvas.width, drawCanvas.height)
                cutCtx.fill = Color.rgb(0, 0, 0, 0.5)
                cutCtx.fillRect(0.0, 0.0, drawCanvas.width, drawCanvas.height)
                cutCtx.clearRect(min(prevX, e.x), min(prevY, e.y), abs(e.x - prevX), abs(e.y - prevY))
            }
            else {
                val size = brushSizeSlider.value
                val x = e.x - size / 2
                val y = e.y - size / 2
                if (e.button == MouseButton.SECONDARY) {
                    drawCtx.clearRect(x, y, size, size)
                } else {
                    drawCtx.fill = brushColorPicker.value
                    drawCtx.fillOval(x, y, size, size)
                }
            }
        }
        cutCanvas.onMouseReleased = EventHandler { e ->
            if (cutModeCheckBox.isSelected && (prevX != e.x && prevY != e.y)) {
                cutCtx.clearRect(0.0, 0.0, drawCanvas.width, drawCanvas.height)
                val params = SnapshotParameters()
                params.fill = Color.TRANSPARENT
                val snapImg = imgCanvas.snapshot(params, null)
                val snapDraw = drawCanvas.snapshot(params, null)
                val x = max(min(prevX, e.x), 0.0)
                val y = max(min(prevY, e.y), 0.0)
                val cropWidth = min(abs(e.x - prevX), imgCanvas.width - x)
                val cropHeight = min(abs(e.y - prevY), imgCanvas.height - y)
                val croppedImg = WritableImage(snapImg.pixelReader, x.toInt(), y.toInt(), cropWidth.toInt(), cropHeight.toInt())
                val croppedDraw = WritableImage(snapDraw.pixelReader, x.toInt(), y.toInt(), cropWidth.toInt(), cropHeight.toInt())

                refreshCanvas(imgCanvas, drawCanvas, cutCanvas, croppedImg)
                drawCtx.drawImage(croppedDraw, x, y, cropWidth, cropHeight)
            }
        }

        val takeScreenButton = Button("Screenshot")
        takeScreenButton.onAction = EventHandler {
            takeScreenshot(hideCheckbox.isSelected, delaySlider.value.toLong(), imgCanvas, drawCanvas, cutCanvas, primaryStage)
        }

        tools.children.addAll(takeScreenButton, hideCheckbox, delayVBox, brushColorPicker, brushSizeVBox, cutModeCheckBox)


        // Меню
        val menuBar = MenuBar()
        val menuFile = Menu("File")
        val menuItemOpen = MenuItem("Open")
        menuItemOpen.onAction = EventHandler {
            openImage(imgCanvas, drawCanvas, cutCanvas, primaryStage)
        }
        menuItemOpen.accelerator = KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN)

        val menuItemSave = MenuItem("Save")
        menuItemSave.onAction = EventHandler {
            saveImage(true, imgCanvas, drawCanvas, primaryStage)
        }
        menuItemSave.accelerator = KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN)
        val menuItemSaveAs = MenuItem("Save as")
        menuItemSaveAs.onAction = EventHandler {
            saveImage(false, imgCanvas, drawCanvas, primaryStage)
        }
        menuItemSaveAs.accelerator = KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN)
        menuFile.items.addAll(menuItemOpen, menuItemSave, menuItemSaveAs)

        val menuOther = Menu("Other")
        val menuItemHelp = MenuItem("Help")
        menuItemHelp.onAction = EventHandler {
            showHelp()
        }
        menuItemHelp.accelerator = KeyCodeCombination(KeyCode.H, KeyCombination.CONTROL_DOWN)

        val menuItemQuit = MenuItem("Quit")
        menuItemQuit.onAction = EventHandler {
            Platform.exit()
        }
        menuItemQuit.accelerator = KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN)
        menuOther.items.addAll(menuItemHelp, menuItemQuit)

        menuBar.menus.addAll(menuFile, menuOther)

        val root = VBox(menuBar, tools, scrollPane)

        // CSS
        tools.style = "-fx-alignment: center; -fx-spacing: 10; -fx-background-color: #CCCCCC;" +
                "-fx-border-radius: 2; -fx-border-color: #000000; -fx-padding: 5 5 5 5"

        //Создание окна
        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val scene = Scene(root, screenSize.getWidth() / 2, screenSize.getHeight() / 2)

        primaryStage.title = "ScreenAndDraw"
        primaryStage.scene = scene
        primaryStage.show()
        primaryStage.isMaximized = true
    }

    private fun showHelp() {
        val stage = Stage()
        val text = TextArea(
            "When cut checkbox off:\n" +
                    "\tLMB - Draw\n" +
                    "\tRMB - Clear\n" +
                "When cut checkbox on:\n" +
                    "\tLMB - Choose area\n" +
                    "Ctrl + O - Open new file\n" +
                    "Ctrl + S - Quick save in default directory\n" +
                    "Ctrl + Shift + S - Save file\n" +
                    "Ctrl + Q - Quit application\n" +
                    "Ctrl + H - Show help")
        text.isEditable = false
        val scene = Scene(text)
        stage.title = "Help"
        stage.scene = scene
        stage.showAndWait()
    }

    private fun takeScreenshot(isHide: Boolean, delay: Long, imgC : Canvas, drawC : Canvas, cutC : Canvas, stage: Stage) {
        if (isHide) {
            stage.hide()
            Thread.sleep(200)
        }
        Thread.sleep(delay * 1000)
        try {
            val robot = Robot()
            val screenRect = Rectangle(Toolkit.getDefaultToolkit().screenSize)
            val image = SwingFXUtils.toFXImage(robot.createScreenCapture(screenRect), null)
            refreshCanvas(imgC, drawC, cutC, image)
        } catch (ex: IOException) {
            print(ex)
        }
        stage.show()
    }

    private fun refreshCanvas(c1:Canvas, c2: Canvas, c3:Canvas, image: Image?) {
        if (image != null) {
            c2.graphicsContext2D.clearRect(0.0, 0.0, c2.width, c2.height)
            image.height.also {
                c1.height = it
                c2.height = it
                c3.height = it
            }
            image.width.also {
                c1.width = it
                c2.width = it
                c3.width = it
            }
            c1.graphicsContext2D.drawImage(image, 0.0, 0.0)
        }
    }

    private fun openImage(imgC : Canvas, drawC : Canvas, cutC : Canvas, stage: Stage) {
        val fileChooser = FileChooser()
        fileChooser.extensionFilters.add(FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png"))
        fileChooser.initialDirectory = File(getLastDir())
        val file = fileChooser.showOpenDialog(stage)
        if (file != null) {
            setLastDir(file)
            val image = Image(file.toURI().toString())
            refreshCanvas(imgC, drawC, cutC, image)
        }
    }

    private fun saveImage(isQuick: Boolean, imgC : Canvas, drawC : Canvas, stage: Stage) {
        val fileName = SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(Calendar.getInstance().time) + ".png"
        val file: File
        if (isQuick) {
            file = File(defaultPath + fileName)
        } else {
            val directoryChooser = DirectoryChooser()
            directoryChooser.initialDirectory = File(getLastDir())
            val dir = directoryChooser.showDialog(stage) ?: return
            file = File(dir.toString() + fileName)
        }
        val params = SnapshotParameters()
        params.fill = Color.TRANSPARENT
        val snapImg = imgC.snapshot(params, null)
        val snapDraw = drawC.snapshot(params, null)
        val result = Canvas(imgC.width, drawC.height)
        val resultCtx = result.graphicsContext2D
        resultCtx.drawImage(snapImg, 0.0, 0.0)
        resultCtx.drawImage(snapDraw, 0.0, 0.0)
        ImageIO.write(SwingFXUtils.fromFXImage(result.snapshot(params, null), null), "png", file)
        if (!isQuick) {
            setLastDir(file)
        }
    }

    //Манипуляции с последней директорией
    private fun setLastDir(file: File) {
        try {
            BufferedWriter(FileWriter(defaultPath + cfgName)).use { bw ->
                bw.write(file.parent)
            }
        } catch (ex: IOException) {
            print(ex)
        }
    }

    private fun getLastDir(): String {
        return try {
            BufferedReader(FileReader(defaultPath + cfgName)).readLine()
        } catch (ex: IOException) {
            print(ex)
            defaultPath
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            launch(ScreenAndDraw::class.java)
        }
    }
}