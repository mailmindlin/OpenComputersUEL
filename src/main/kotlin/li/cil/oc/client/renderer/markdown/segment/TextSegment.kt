package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.markdown.Document
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.renderer.GlStateManager
import java.util.regex.MatchResult
import java.util.regex.Pattern

internal open class TextSegment(override val parent: Segment?, override val text: String) : BasicTextSegment {
    override var next: Segment? = null

    override fun render(x: Int, y: Int, indent: Int, maxWidth: Int, renderer: FontRenderer, mouseX: Int, mouseY: Int): InteractiveSegment? {
        var currentX = x + indent
        var currentY = y
        var chars = text
        if (indent == 0) chars = chars.dropWhile { it.isWhitespace() }
        val wrapIndent = computeWrapIndent(renderer)
        var numChars = maxChars(chars, maxWidth - indent, maxWidth - wrapIndent, renderer)
        var hovered: InteractiveSegment? = null
        while (chars.isNotEmpty()) {
            val part = chars.take(numChars)
            hovered = hovered ?: resolvedInteractive?.checkHovered(mouseX, mouseY, currentX, currentY, stringWidth(part, renderer), (Document.lineHeight(renderer) * resolvedScale).toInt())
            GlStateManager.pushMatrix()
            GlStateManager.translate(currentX.toFloat(), currentY.toFloat(), 0f)
            GlStateManager.scale(resolvedScale, resolvedScale, resolvedScale)
            GlStateManager.translate(-currentX.toFloat(), -currentY.toFloat(), 0f)
            renderer.drawString(resolvedFormat + part, currentX, currentY, resolvedColor)
            GlStateManager.popMatrix()
            currentX = x + wrapIndent
            currentY += lineHeight(renderer)
            chars = chars.drop(numChars).dropWhile { it.isWhitespace() }
            numChars = maxChars(chars, maxWidth - wrapIndent, maxWidth - wrapIndent, renderer)
        }

        return hovered
    }

    override fun refine(pattern: Pattern, factory: (Segment, MatchResult) -> Segment): Iterable<Segment> {
        val result = mutableListOf<Segment>()

        // Keep track of last matches end, to generate plain text segments.
        var textStart = 0
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            // Create segment for leading plain text.
            if (matcher.start() > textStart) {
                result.add(TextSegment(this, text.substring(textStart, matcher.start())))
            }
            textStart = matcher.end()

            // Create segment for formatted text.
            result.add(factory(this, matcher.toMatchResult()))
        }

        // Create segment for remaining plain text.
        if (textStart == 0) {
            result.add(this)
        } else if (textStart < text.length) {
            result.add(TextSegment(this, text.substring(textStart)))
        }
        return result
    }

    // ----------------------------------------------------------------------- //

    override fun lineHeight(renderer: FontRenderer): Int = (super.lineHeight(renderer) * resolvedScale).toInt()

    override fun stringWidth(s: String, renderer: FontRenderer): Int = (renderer.getStringWidth(resolvedFormat + s) * resolvedScale).toInt()

    // ----------------------------------------------------------------------- //

    protected open val color: Int?
        get() = null

    protected open val scale: Float?
        get() = null

    protected open val format: String
        get() = ""

    private val resolvedColor: Int
        get() = color ?: (parent as? TextSegment)?.resolvedColor ?: 0xDDDDDD

    private val resolvedScale: Float
        get() = (parent as? TextSegment)?.let { scale ?: 1f * it.resolvedScale } ?: 1f

    private val resolvedFormat: String
        get() = (parent as? TextSegment)?.let { it.resolvedFormat + format } ?: format

    private val resolvedInteractive: InteractiveSegment?
        get() = this as? InteractiveSegment ?: (parent as? TextSegment)?.resolvedInteractive
}
