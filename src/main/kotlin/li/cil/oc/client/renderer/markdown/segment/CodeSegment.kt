package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.TextBufferRenderCache
import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.renderer.GlStateManager

internal class CodeSegment(override val parent: Segment?, override val text: String) : BasicTextSegment {
    override var next: Segment? = null

    override fun render(x: Int, y: Int, indent: Int, maxWidth: Int, renderer: FontRenderer, mouseX: Int, mouseY: Int): InteractiveSegment? {
        TextBufferRenderCache.renderer.generateChars(text.toCharArray())

        var currentX = x + indent
        var currentY = y
        var chars = text
        val wrapIndent = computeWrapIndent(renderer)
        var numChars = maxChars(chars, maxWidth - indent, maxWidth - wrapIndent, renderer)
        while (chars.isNotEmpty()) {
            val part = chars.take(numChars)
            GlStateManager.color(0.75f, 0.8f, 1f, 1f)
            TextBufferRenderCache.renderer.drawString(part, currentX, currentY)
            currentX = x + wrapIndent
            currentY += lineHeight(renderer)
            chars = chars.drop(numChars).dropWhile { it.isWhitespace() }
            numChars = maxChars(chars, maxWidth - wrapIndent, maxWidth - wrapIndent, renderer)
        }

        return null
    }

    override val ignoreLeadingWhitespace: Boolean
        get() = false

    override fun stringWidth(s: String, renderer: FontRenderer): Int = s.length * TextBufferRenderCache.renderer.charRenderWidth

    override fun toString(format: MarkupFormat): String = when (format) {
        MarkupFormat.Markdown -> "`$text`"
        MarkupFormat.IGWMod -> "[prefix{1}]$text [prefix{}]"
    }
}
