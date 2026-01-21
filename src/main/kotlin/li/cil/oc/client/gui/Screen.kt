package li.cil.oc.client.gui

import li.cil.oc.api
import li.cil.oc.client.renderer.TextBufferRenderCache
import li.cil.oc.client.renderer.gui.BufferRenderer
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.input.Mouse

class Screen(
    private val buffer: api.internal.TextBuffer,
    val hasMouse: Boolean,
    val hasKeyboardCallback: () -> Boolean,
    val hasPower: () -> Boolean
) : traits.InputBuffer {

    override fun buffer(): api.internal.TextBuffer = buffer

    override fun hasKeyboard() = hasKeyboardCallback()

    override fun bufferX() = 8 + x

    override fun bufferY() = 8 + y

    private val bufferMargin = BufferRenderer.margin + BufferRenderer.innerMargin

    private var didClick = false

    private var x = 0
    private var y = 0

    private var mx = -1
    private var my = -1

    override fun handleMouseInput() {
        super.handleMouseInput()
        if (hasMouse && Mouse.hasWheel() && Mouse.getEventDWheel() != 0) {
            val mouseX = Mouse.getEventX() * width / mc.displayWidth
            val mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1
            toBufferCoordinates(mouseX, mouseY)?.let { (bx, by) ->
                val scroll = Mouse.getEventDWheel().toDouble().sign.toInt()
                buffer.mouseScroll(bx, by, scroll, null)
            }
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int) {
        super.mouseClicked(mouseX, mouseY, button)
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
                    buffer.mouseUp(bx, by, button, null)
                } ?: buffer.mouseUp(-1.0, -1.0, button, null)
            }
            didClick = false
            mx = -1
            my = -1
        }
    }

    private fun clickOrDrag(mouseX: Int, mouseY: Int, button: Int) {
        toBufferCoordinates(mouseX, mouseY)?.let { (bx, by) ->
            if (bx.toInt() != mx || (by * 2).toInt() != my) {
                if (mx >= 0 && my >= 0) buffer.mouseDrag(bx, by, button, null)
                else buffer.mouseDown(bx, by, button, null)
                didClick = true
                mx = bx.toInt()
                my = (by * 2).toInt() // for high precision mode, sends some unnecessary packets when not using it, but eh
            }
        }
    }

    private fun toBufferCoordinates(mouseX: Int, mouseY: Int): Pair<Double, Double>? {
        val bx = (mouseX - x - bufferMargin) / scale / TextBufferRenderCache.renderer.charRenderWidth
        val by = (mouseY - y - bufferMargin) / scale / TextBufferRenderCache.renderer.charRenderHeight
        val bw = buffer.viewportWidth
        val bh = buffer.viewportHeight
        return if (bx >= 0 && by >= 0 && bx < bw && by < bh) Pair(bx, by)
        else null
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, dt: Float) {
        super.drawScreen(mouseX, mouseY, dt)
        drawBufferLayer()
    }

    override fun drawBuffer() {
        GlStateManager.translate(x.toFloat(), y.toFloat(), 0f)
        BufferRenderer.drawBackground()
        if (hasPower()) {
            GlStateManager.translate(bufferMargin.toFloat(), bufferMargin.toFloat(), 0f)
            GlStateManager.scale(scale.toFloat(), scale.toFloat(), 1f)
            RenderState.makeItBlend()
            BufferRenderer.drawText(buffer)
        }
    }

    override fun changeSize(w: Double, h: Double, recompile: Boolean): Double {
        val bw = buffer.renderWidth
        val bh = buffer.renderHeight
        val scaleX = Math.min(width / (bw + bufferMargin * 2.0), 1.0)
        val scaleY = Math.min(height / (bh + bufferMargin * 2.0), 1.0)
        val scale = Math.min(scaleX, scaleY)
        val innerWidth = (bw * scale).toInt()
        val innerHeight = (bh * scale).toInt()
        x = (width - (innerWidth + bufferMargin * 2)) / 2
        y = (height - (innerHeight + bufferMargin * 2)) / 2
        if (recompile) {
            BufferRenderer.compileBackground(innerWidth, innerHeight)
        }
        return scale
    }
}
