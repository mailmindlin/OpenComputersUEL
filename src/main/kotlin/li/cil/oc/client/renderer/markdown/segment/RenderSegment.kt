package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.api.manual.ImageRenderer
import li.cil.oc.api.manual.InteractiveImageRenderer
import li.cil.oc.client.renderer.markdown.Document
import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.opengl.GL11
import kotlin.math.ceil
import kotlin.math.min

internal class RenderSegment(override val parent: Segment?, val title: String, val imageRenderer: ImageRenderer) : InteractiveSegment {
    override var next: Segment? = null
    var lastX = 0
    var lastY = 0

    override val tooltip: String?
        get() = (imageRenderer as? InteractiveImageRenderer)?.getTooltip(title) ?: title

    override fun onMouseClick(mouseX: Int, mouseY: Int): Boolean = when (imageRenderer) {
        is InteractiveImageRenderer -> imageRenderer.onMouseClick(mouseX - lastX, mouseY - lastY)
        else -> false
    }

    private fun scale(maxWidth: Int) = min(1f, maxWidth / imageRenderer.width.toFloat())

    fun imageWidth(maxWidth: Int) = min(maxWidth, imageRenderer.width)

    fun imageHeight(maxWidth: Int) = ceil(imageRenderer.height * scale(maxWidth)).toInt() + 4

    override fun nextY(indent: Int, maxWidth: Int, renderer: FontRenderer): Int =
        imageHeight(maxWidth) + (if (indent > 0) Document.lineHeight(renderer) else 0)

    override fun nextX(indent: Int, maxWidth: Int, renderer: FontRenderer): Int = 0

    override fun render(x: Int, y: Int, indent: Int, maxWidth: Int, renderer: FontRenderer, mouseX: Int, mouseY: Int): InteractiveSegment? {
        val width = imageWidth(maxWidth)
        val height = imageHeight(maxWidth)
        val xOffset = (maxWidth - width) / 2
        val yOffset = 2 + (if (indent > 0) Document.lineHeight(renderer) else 0)
        val s = scale(maxWidth)

        lastX = x + xOffset
        lastY = y + yOffset

        val hovered = checkHovered(mouseX, mouseY, x + xOffset, y + yOffset, width, height)

        GlStateManager.pushMatrix()
        GlStateManager.translate((x + xOffset).toFloat(), (y + yOffset).toFloat(), 0f)
        GlStateManager.scale(s, s, s)

        GlStateManager.enableBlend()
        GlStateManager.enableAlpha()

        if (hovered != null) {
            GlStateManager.color(1f, 1f, 1f, 0.15f)
            GlStateManager.disableTexture2D()
            GL11.glBegin(GL11.GL_QUADS)
            GL11.glVertex2f(0f, 0f)
            GL11.glVertex2f(0f, imageRenderer.height.toFloat())
            GL11.glVertex2f(imageRenderer.width.toFloat(), imageRenderer.height.toFloat())
            GL11.glVertex2f(imageRenderer.width.toFloat(), 0f)
            GL11.glEnd()
            GlStateManager.enableTexture2D()
        }

        GlStateManager.color(1f, 1f, 1f, 1f)

        imageRenderer.render(mouseX - x, mouseY - y)

        GlStateManager.disableBlend()
        GlStateManager.disableAlpha()
        GlStateManager.disableLighting()

        GlStateManager.popMatrix()

        return hovered
    }

    override fun toString(format: MarkupFormat): String = when (format) {
        MarkupFormat.Markdown -> "![$title]($imageRenderer)"
        MarkupFormat.IGWMod -> "(Sorry, images only work in the OpenComputers manual for now.)" // TODO
    }
}
