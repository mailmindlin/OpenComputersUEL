package li.cil.oc.client.renderer.font

import li.cil.oc.Settings
import li.cil.oc.util.ExtendedUnicodeHelper
import li.cil.oc.util.PackedColor
import li.cil.oc.util.RenderState
import li.cil.oc.util.TextBuffer
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.opengl.GL11
import kotlin.math.min

/**
 * Base class for texture based font rendering.
 *
 * Provides common logic for the static one (using an existing texture) and the
 * dynamic one (generating textures on the fly from a font).
 */
abstract class TextureFontRenderer {
    protected val basicChars = """☺☻♥♦♣♠•◘○◙♂♀♪♫☼►◄↕‼¶§▬↨↑↓→←∟↔▲▼ !"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\]^_`abcdefghijklmnopqrstuvwxyz{|}~⌂ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜ¢£¥₧ƒáíóúñÑªº¿⌐¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘┌█▄▌▐▀αßΓπΣσµτΦΘΩδ∞φε∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■"""

    val charRenderWidth: Int
        get() = charWidth / 2

    val charRenderHeight: Int
        get() = charHeight / 2

    /**
     * If drawString() is called inside display lists this should be called
     * beforehand, outside the display list, to ensure no characters have to
     * be generated inside the draw call.
     */
    fun generateChars(chars: CharArray) {
        GlStateManager.enableTexture2D()
        for (char in chars) {
            generateChar(char.code)
        }
    }

    fun generateChars(chars: IntArray) {
        for (char in chars) {
            generateChar(char)
        }
    }

    fun drawBuffer(buffer: TextBuffer, viewportWidth: Int, viewportHeight: Int) {
        val format = buffer.format

        GlStateManager.pushMatrix()
        RenderState.pushAttrib()

        GlStateManager.scale(0.5f, 0.5f, 1f)

        GL11.glDepthMask(false)
        RenderState.makeItBlend()
        GL11.glDisable(GL11.GL_TEXTURE_2D)

        RenderState.checkError(javaClass.name + ".drawBuffer: configure state")

        // Background first. We try to merge adjacent backgrounds of the same
        // color to reduce the number of quads we have to draw.
        GL11.glBegin(GL11.GL_QUADS)
        for (y in 0 until min(viewportHeight, buffer.height)) {
            val color = buffer.color[y]
            var cbg = 0x000000u
            var x = 0
            var width = 0
            for (col in color.map { PackedColor.unpackBackground(it.toUShort(), format) }.takeWhile { x + width < viewportWidth }) {
                if (col != cbg) {
                    drawQuad(cbg.toInt(), x, y, width)
                    cbg = col
                    x += width
                    width = 0
                }
                width += 1
            }
            drawQuad(cbg.toInt(), x, y, width)
        }
        GL11.glEnd()

        RenderState.checkError(javaClass.name + ".drawBuffer: background")

        GL11.glEnable(GL11.GL_TEXTURE_2D)

        if (Settings.get.textLinearFiltering) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        }

        // Foreground second. We only have to flush when the color changes, so
        // unless every char has a different color this should be quite efficient.
        for (y in 0 until min(viewportHeight, buffer.height)) {
            val line = buffer.buffer[y]
            val color = buffer.color[y]
            val ty = y * charHeight
            for (i in 0 until textureCount) {
                bindTexture(i)
                GL11.glBegin(GL11.GL_QUADS)
                var cfg = (-1).toUInt()
                var tx = 0f
                for (n in 0 until viewportWidth) {
                    val ch = line[n]
                    val col = PackedColor.unpackForeground(color[n].toUShort(), format)
                    // Check if color changed.
                    if (col != cfg) {
                        cfg = col
                        GL11.glColor3f(
                            ((cfg and 0xFF0000u) shr 16).toInt() / 255f,
                            ((cfg and 0x00FF00u) shr 8).toInt() / 255f,
                            ((cfg and 0x0000FFu) shr 0).toInt() / 255f
                        )
                    }
                    // Don't render whitespace.
                    if (ch != ' '.code) {
                        drawChar(tx, ty.toFloat(), ch)
                    }
                    tx += charWidth
                }
                GL11.glEnd()
            }
        }

        RenderState.checkError(javaClass.name + ".drawBuffer: foreground")

        GlStateManager.bindTexture(0)
        GL11.glDepthMask(true)
        GL11.glColor3f(1f, 1f, 1f)
        RenderState.disableBlend()
        RenderState.popAttrib()
        GlStateManager.popMatrix()

        RenderState.checkError(javaClass.name + ".drawBuffer: leaving")
    }

    fun drawString(s: String, x: Int, y: Int) {
        val sLength = ExtendedUnicodeHelper.length(s)

        GlStateManager.pushMatrix()
        RenderState.pushAttrib()

        GlStateManager.translate(x.toFloat(), y.toFloat(), 0f)
        GlStateManager.scale(0.5f, 0.5f, 1f)
        GlStateManager.depthMask(false)

        for (i in 0 until textureCount) {
            bindTexture(i)
            GL11.glBegin(GL11.GL_QUADS)
            var tx = 0f
            var cx = 0
            for (n in 0 until sLength) {
                val ch = s.codePointAt(cx)
                // Don't render whitespace.
                if (ch != ' '.code) {
                    drawChar(tx, 0f, ch)
                }
                tx += charWidth
                cx = s.offsetByCodePoints(cx, 1)
            }
            GL11.glEnd()
        }

        RenderState.popAttrib()
        GlStateManager.popMatrix()
        GlStateManager.color(1f, 1f, 1f)
    }

    protected abstract val charWidth: Int

    protected abstract val charHeight: Int

    protected abstract val textureCount: Int

    protected abstract fun bindTexture(index: Int)

    protected abstract fun generateChar(char: Int)

    protected abstract fun drawChar(tx: Float, ty: Float, char: Int)

    private fun drawQuad(color: Int, x: Int, y: Int, width: Int) {
        if (color != 0 && width > 0) {
            val x0 = x * charWidth
            val x1 = (x + width) * charWidth
            val y0 = y * charHeight
            val y1 = (y + 1) * charHeight
            GlStateManager.color(
                ((color shr 16) and 0xFF) / 255f,
                ((color shr 8) and 0xFF) / 255f,
                (color and 0xFF) / 255f
            )
            GL11.glVertex3d(x0.toDouble(), y1.toDouble(), 0.0)
            GL11.glVertex3d(x1.toDouble(), y1.toDouble(), 0.0)
            GL11.glVertex3d(x1.toDouble(), y0.toDouble(), 0.0)
            GL11.glVertex3d(x0.toDouble(), y0.toDouble(), 0.0)
        }
    }
}
