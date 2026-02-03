package li.cil.oc.client.gui.traits

import li.cil.oc.client.KeyBindings
import li.cil.oc.client.Textures
import li.cil.oc.integration.util.ItemSearch
import li.cil.oc.util.RenderState
import li.cil.oc.api.internal.TextBuffer
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.inventory.GuiContainer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11

internal interface InputBuffer : DisplayBuffer {
    val buffer: TextBuffer?

    override val bufferColumns: Int
        get() = if (buffer == null) 0 else buffer!!.viewportWidth

    override val bufferRows: Int
        get() = if (buffer == null) 0 else buffer!!.viewportHeight

    val hasKeyboard: Boolean

    val pressedKeys: MutableMap<Int, Char>
    var showKeyboardMissing: Long

    override fun initGui() {
        super.initGui()
        Keyboard.enableRepeatEvents(true)
    }

    override fun drawBufferLayer() {
        super.drawBufferLayer()

        if (System.currentTimeMillis() - showKeyboardMissing < 1000) {
            Textures.bind(Textures.GUI.KeyboardMissing)
            GlStateManager.disableDepth()

            val x = bufferX + buffer!!.renderWidth() - 16
            val y = bufferY + buffer!!.renderHeight() - 16

            val t = Tessellator.getInstance()
            val r = t.buffer
            r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
            r.pos(x.toDouble(), (y + 16).toDouble(), 0.0).tex(0.0, 1.0).endVertex()
            r.pos((x + 16).toDouble(), (y + 16).toDouble(), 0.0).tex(1.0, 1.0).endVertex()
            r.pos((x + 16).toDouble(), y.toDouble(), 0.0).tex(1.0, 0.0).endVertex()
            r.pos(x.toDouble(), y.toDouble(), 0.0).tex(0.0, 0.0).endVertex()
            t.draw()

            GlStateManager.enableDepth()

            RenderState.checkError(this.javaClass.name + ".drawBufferLayer: keyboard icon")
        }
    }

    fun onGuiClosed() {
        if (buffer != null) {
            for ((code, char) in pressedKeys) {
                buffer!!.keyUp(char, code, null)
            }
        }
        Keyboard.enableRepeatEvents(false)
    }

    fun handleKeyboardInput() {
        if (this is GuiContainer && ItemSearch.isInputFocused()) return

        val code = Keyboard.getEventKey()
        val buffer = buffer ?: return
        if (code == Keyboard.KEY_ESCAPE || code == Keyboard.KEY_F11) return
        if (!hasKeyboard) {
            showKeyboardMissing = System.currentTimeMillis()
            return
        }
        if (Keyboard.getEventKeyState()) {
            val char = Keyboard.getEventCharacter()
            if (!pressedKeys.containsKey(code) || !ignoreRepeat(char, code)) {
                buffer.keyDown(char, code, null)
                pressedKeys[code] = char
            }
        } else {
            val char = pressedKeys.remove(code)
            if (char != null) {
                buffer.keyUp(char, code, null)
            }
            // Else: Wasn't pressed while viewing the screen.
        }

        if (KeyBindings.isPastingClipboard) {
            buffer.clipboard(GuiScreen.getClipboardString(), null)
        }
    }

    fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        val isMiddleMouseButton = mouseButton == 2
        val isBoundMouseButton = KeyBindings.isPastingClipboard
        if (buffer != null && (isMiddleMouseButton || isBoundMouseButton)) {
            if (hasKeyboard) {
                buffer!!.clipboard(GuiScreen.getClipboardString(), null)
            } else {
                showKeyboardMissing = System.currentTimeMillis()
            }
        }
    }

    private fun ignoreRepeat(char: Char, code: Int): Boolean {
        return code == Keyboard.KEY_LCONTROL ||
            code == Keyboard.KEY_RCONTROL ||
            code == Keyboard.KEY_LMENU ||
            code == Keyboard.KEY_RMENU ||
            code == Keyboard.KEY_LSHIFT ||
            code == Keyboard.KEY_RSHIFT ||
            code == Keyboard.KEY_LMETA ||
            code == Keyboard.KEY_RMETA
    }
}
