package li.cil.oc.client.renderer.markdown

import li.cil.oc.api.Manual
import li.cil.oc.client.renderer.markdown.segment.*
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.opengl.GL11

/**
 * Primitive Markdown parser, only supports a very small subset. Used for
 * parsing documentation into segments, to be displayed in a GUI somewhere.
 *
 * General usage is: parse a string using parse(), render it using render().
 *
 * The parser generates a list of segments, each segment representing a part
 * of the document, with a specific formatting / render type. For example,
 * links are their own segments, a bold section in a link would be its own
 * section and so on.
 * The data structure is essentially a very flat multi-tree, where the segments
 * returned are the leaves, and the roots are the individual lines, represented
 * as text segments.
 * Formatting is done by accumulating formatting information over the parent
 * nodes, up to the root.
 */
object Document {
    /**
     * Parses a plain text document into a list of segments.
     */
    fun parse(document: Iterable<String>): Segment {
        var segments: Iterable<Segment> = document.map { line ->
            TextSegment(null, line.trimEnd())
        }
        for ((pattern, factory) in segmentTypes) {
            segments = segments.flatMap { it.refine(pattern, factory) }
        }
        val segmentList = segments.toList()
        for (i in 0 until segmentList.size - 1) {
            segmentList[i].next = segmentList[i + 1]
        }
        return segmentList.first()
    }

    /**
     * Compute the overall height of a document, e.g. for computation of scroll offsets.
     */
    fun height(document: Segment, maxWidth: Int, renderer: FontRenderer): Int {
        var currentX = 0
        var currentY = 0
        var segment: Segment? = document
        while (segment != null) {
            currentY += segment.nextY(currentX, maxWidth, renderer)
            currentX = segment.nextX(currentX, maxWidth, renderer)
            segment = segment.next
        }
        return currentY
    }

    /**
     * Line height for a normal line of text.
     */
    fun lineHeight(renderer: FontRenderer): Int = renderer.FONT_HEIGHT + 1

    /**
     * Renders a list of segments and tooltips if a segment with a tooltip is hovered.
     * Returns the hovered interactive segment, if any.
     */
    fun render(
        document: Segment,
        x: Int,
        y: Int,
        maxWidth: Int,
        maxHeight: Int,
        yOffset: Int,
        renderer: FontRenderer,
        mouseX: Int,
        mouseY: Int
    ): InteractiveSegment? {
        val mc = Minecraft.getMinecraft()

        RenderState.pushAttrib()

        // On some systems/drivers/graphics cards the next calls won't update the
        // depth buffer correctly if alpha test is enabled. Guess how we found out?
        // By noticing that on those systems it only worked while chat messages
        // were visible. Yeah. I know.
        GlStateManager.disableAlpha()

        // Clear depth mask, then create masks in foreground above and below scroll area.
        GlStateManager.color(1f, 1f, 1f, 1f)
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT)
        GlStateManager.enableDepth()
        GlStateManager.depthFunc(GL11.GL_LEQUAL)
        GlStateManager.depthMask(true)
        GlStateManager.colorMask(false, false, false, false)

        GlStateManager.pushMatrix()
        GlStateManager.translate(0.0, 0.0, 500.0)
        GL11.glBegin(GL11.GL_QUADS)
        GL11.glVertex2f(0f, y.toFloat())
        GL11.glVertex2f(mc.displayWidth.toFloat(), y.toFloat())
        GL11.glVertex2f(mc.displayWidth.toFloat(), 0f)
        GL11.glVertex2f(0f, 0f)
        GL11.glVertex2f(0f, mc.displayHeight.toFloat())
        GL11.glVertex2f(mc.displayWidth.toFloat(), mc.displayHeight.toFloat())
        GL11.glVertex2f(mc.displayWidth.toFloat(), (y + maxHeight).toFloat())
        GL11.glVertex2f(0f, (y + maxHeight).toFloat())
        GL11.glEnd()
        GlStateManager.popMatrix()
        GlStateManager.colorMask(true, true, true, true)

        // Actual rendering.
        var hovered: InteractiveSegment? = null
        var indent = 0
        var currentY = y - yOffset
        val minY = y - lineHeight(renderer)
        val maxY = y + maxHeight + lineHeight(renderer)
        var segment: Segment? = document
        while (segment != null) {
            val segmentHeight = segment.nextY(indent, maxWidth, renderer)
            if (currentY + segmentHeight >= minY && currentY <= maxY) {
                val result = segment.render(x, currentY, indent, maxWidth, renderer, mouseX, mouseY)
                hovered = hovered ?: result
            }
            currentY += segmentHeight
            indent = segment.nextX(indent, maxWidth, renderer)
            segment = segment.next
        }
        if (mouseX < x || mouseX > x + maxWidth || mouseY < y || mouseY > y + maxHeight) {
            hovered = null
        }
        hovered?.notifyHover()

        RenderState.popAttrib()
        GlStateManager.bindTexture(0)

        return hovered
    }

    // ----------------------------------------------------------------------- //

    private fun headerSegment(s: Segment, m: MatchResult) =
        HeaderSegment(s, m.groupValues[2], m.groupValues[1].length)

    private fun codeSegment(s: Segment, m: MatchResult) =
        CodeSegment(s, m.groupValues[2])

    private fun linkSegment(s: Segment, m: MatchResult) =
        LinkSegment(s, m.groupValues[1], m.groupValues[2])

    private fun boldSegment(s: Segment, m: MatchResult) =
        BoldSegment(s, m.groupValues[2])

    private fun italicSegment(s: Segment, m: MatchResult) =
        ItalicSegment(s, m.groupValues[2])

    private fun strikethroughSegment(s: Segment, m: MatchResult) =
        StrikethroughSegment(s, m.groupValues[1])

    private fun imageSegment(s: Segment, m: MatchResult): Segment {
        return try {
            val renderer = Manual.imageFor(m.groupValues[2])
            if (renderer != null) {
                RenderSegment(s, m.groupValues[1], renderer)
            } else {
                TextSegment(s, "No renderer found for: " + m.groupValues[2])
            }
        } catch (t: Throwable) {
            TextSegment(s, t.toString() ?: "Unknown error.")
        }
    }

    // ----------------------------------------------------------------------- //

    private val segmentTypes = arrayOf(
        """^(#+)\s(.*)""".toRegex() to ::headerSegment, // headers: # ...
        """(`)(.*?)\1""".toRegex() to ::codeSegment, // code: `...`
        """!\[([^\[]*)\]\(([^\)]+)\)""".toRegex() to ::imageSegment, // images: ![...](...)
        """\[([^\[]+)\]\(([^\)]+)\)""".toRegex() to ::linkSegment, // links: [...](...)
        """(\*\*|__)(\S.*?\S|${'$'})\1""".toRegex() to ::boldSegment, // bold: **...** | __...__
        """(\*|_)(\S.*?\S|${'$'})\1""".toRegex() to ::italicSegment, // italic: *...* | _..._
        """~~(\S.*?\S|${'$'})~~""".toRegex() to ::strikethroughSegment // strikethrough: ~~...~~
    )
}
