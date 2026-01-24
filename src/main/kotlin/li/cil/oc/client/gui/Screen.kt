package li.cil.oc.client.gui

import li.cil.oc.api.internal.TextBuffer
import li.cil.oc.client.gui.traits.DisplayBuffer
import li.cil.oc.client.gui.traits.InputBuffer
import li.cil.oc.client.renderer.TextBufferRenderCache
import li.cil.oc.client.renderer.gui.BufferRenderer
import li.cil.oc.util.RenderState
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.input.Mouse
import kotlin.math.min
import kotlin.math.sign

class Screen(
    private val _buffer: TextBuffer,
    val hasMouse: Boolean,
    val hasKeyboardCallback: () -> Boolean,
    val hasPower: () -> Boolean
) : GuiScreen(), InputBuffer {

    override fun doesGuiPauseGame(): Boolean = false

    override val buffer: TextBuffer get() = _buffer

    override val hasKeyboard: Boolean get() = hasKeyboardCallback()

    override val bufferX: Int get() = 8 + x

    override val bufferY: Int get() = 8 + y

    override val pressedKeys: MutableMap<Int, Char> = mutableMapOf()

    override var showKeyboardMissing: Long = 0L

    private val bufferMargin = BufferRenderer.margin + BufferRenderer.innerMargin

    private var didClick = false

    override var displayBufferState: DisplayBuffer.State = DisplayBuffer.State()

    private var x = 0
    private var y = 0

    private var mx = -1
    private var my = -1

    override fun initGui() {
        super<GuiScreen>.initGui()
        initGuiInputBuffer()
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        if (hasMouse && Mouse.hasWheel() && Mouse.getEventDWheel() != 0) {
            val mouseX = Mouse.getEventX() * width / mc.displayWidth
            val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1
            toBufferCoordinates(mouseX, mouseY)?.let { (bx, by) ->
                val scroll = Mouse.getEventDWheel().toDouble().sign.toInt()
                _buffer.mouseScroll(bx, by, scroll, null)
            }
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int) {
        super.mouseClicked(mouseX, mouseY, button)
        mouseClickedInputBuffer(mouseX, mouseY, button)
        if (hasMouse) {
            if (button == 0 || button == 1) {
                clickOrDrag(mouseX, mouseY, button)
            }
        }
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, button: Int, timeSinceLast: Long) {
        super.mouseClickMove(mouseX, mouseY, button, timeSinceLast)
        if (hasMouse && timeSinceLast > 10) {
            if (button == 0 || button == 1) {
                clickOrDrag(mouseX, mouseY, button)
            }
        }
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, button: Int) {
        super.mouseReleased(mouseX, mouseY, button)
        if (hasMouse && button >= 0) {
            if (didClick) {
                toBufferCoordinates(mouseX, mouseY)?.let { (bx, by) ->
                    _buffer.mouseUp(bx, by, button, null)
                } ?: _buffer.mouseUp(-1.0, -1.0, button, null)
            }
            didClick = false
            mx = -1
            my = -1
        }
    }

    override fun handleKeyboardInput() {
        super.handleKeyboardInput()
        handleKeyboardInputBuffer(this)
    }

    override fun onGuiClosed() {
        super.onGuiClosed()
        onGuiClosedInputBuffer()
    }

    private fun clickOrDrag(mouseX: Int, mouseY: Int, button: Int) {
        toBufferCoordinates(mouseX, mouseY)?.let { (bx, by) ->
            if (bx.toInt() != mx || (by * 2).toInt() != my) {
                if (mx >= 0 && my >= 0) _buffer.mouseDrag(bx, by, button, null)
                else _buffer.mouseDown(bx, by, button, null)
                didClick = true
                mx = bx.toInt()
                my = (by * 2).toInt() // for high precision mode, sends some unnecessary packets when not using it, but eh
            }
        }
    }

    private fun toBufferCoordinates(mouseX: Int, mouseY: Int): Pair<Double, Double>? {
        val scale = displayBufferState.scale
        val bx = (mouseX - x - bufferMargin) / scale / TextBufferRenderCache.renderer.charRenderWidth
        val by = (mouseY - y - bufferMargin) / scale / TextBufferRenderCache.renderer.charRenderHeight
        val bw = _buffer.viewportWidth
        val bh = _buffer.viewportHeight
        return if (bx >= 0 && by >= 0 && bx < bw && by < bh) Pair(bx, by)
        else null
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
        this.drawDefaultBackground()
        super.drawScreen(mouseX, mouseY, dt)
        drawBufferLayerWithInput()
    }

    override fun drawBuffer() {
        GlStateManager.translate(x.toFloat(), y.toFloat(), 0f)
        BufferRenderer.drawBackground()
        if (hasPower()) {
            GlStateManager.translate(bufferMargin.toFloat(), bufferMargin.toFloat(), 0f)
            val scale = displayBufferState.scale.toFloat()
            GlStateManager.scale(scale, scale, 1f)
            RenderState.makeItBlend()
            BufferRenderer.drawText(_buffer)
        }
    }

    override fun changeSize(w: Double, h: Double, recompile: Boolean): Double {
        val bw = _buffer.renderWidth()
        val bh = _buffer.renderHeight()
        val scaleX = min(width / (bw + bufferMargin * 2.0), 1.0)
        val scaleY = min(height / (bh + bufferMargin * 2.0), 1.0)
        val newScale = min(scaleX, scaleY)
        val innerWidth = (bw * newScale).toInt()
        val innerHeight = (bh * newScale).toInt()
        x = (width - (innerWidth + bufferMargin * 2)) / 2
        y = (height - (innerHeight + bufferMargin * 2)) / 2
        if (recompile) {
            BufferRenderer.compileBackground(innerWidth, innerHeight)
        }
        return newScale
    }
}
