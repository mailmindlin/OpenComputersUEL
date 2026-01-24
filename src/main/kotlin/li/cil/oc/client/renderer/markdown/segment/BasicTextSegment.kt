package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.markdown.Document
import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.client.gui.FontRenderer

internal interface BasicTextSegment : Segment {
    val breaks: Set<Char>
        get() = setOf(' ', '.', ',', ':', ';', '!', '?', '_', '=', '-', '+', '*', '/', '\\')

    val lists: Set<String>
        get() = setOf("- ", "* ")

    val rootPrefix: String
        get() = (root as TextSegment).text.take(2)

    val text: String

    override fun nextX(indent: Int, maxWidth: Int, renderer: FontRenderer): Int {
        if (isLast) return 0
        var currentX = indent
        var chars = text
        if (ignoreLeadingWhitespace && indent == 0) chars = chars.dropWhile { it.isWhitespace() }
        val wrapIndent = computeWrapIndent(renderer)
        var numChars = maxChars(chars, maxWidth - indent, maxWidth - wrapIndent, renderer)
        while (chars.length > numChars) {
            chars = chars.drop(numChars).dropWhile { it.isWhitespace() }
            numChars = maxChars(chars, maxWidth - wrapIndent, maxWidth - wrapIndent, renderer)
            currentX = wrapIndent
        }
        return currentX + stringWidth(chars, renderer)
    }

    override fun nextY(indent: Int, maxWidth: Int, renderer: FontRenderer): Int {
        var lines = 0
        var chars = text
        if (ignoreLeadingWhitespace && indent == 0) chars = chars.dropWhile { it.isWhitespace() }
        val wrapIndent = computeWrapIndent(renderer)
        var numChars = maxChars(chars, maxWidth - indent, maxWidth - wrapIndent, renderer)
        while (chars.length > numChars) {
            lines++
            chars = chars.drop(numChars).dropWhile { it.isWhitespace() }
            numChars = maxChars(chars, maxWidth - wrapIndent, maxWidth - wrapIndent, renderer)
        }
        if (isLast) lines++
        return lines * lineHeight(renderer)
    }

    override fun toString(format: MarkupFormat): String = text

    // ----------------------------------------------------------------------- //

    val ignoreLeadingWhitespace: Boolean
        get() = true

    fun lineHeight(renderer: FontRenderer): Int = Document.lineHeight(renderer)

    fun stringWidth(s: String, renderer: FontRenderer): Int

    fun maxChars(s: String, maxWidth: Int, maxLineWidth: Int, renderer: FontRenderer): Int {
        var pos = -1
        var lastBreak = -1
        val fullWidth = stringWidth(s, renderer)
        while (pos < s.length) {
            pos++
            val width = stringWidth(s.take(pos), renderer)
            val exceedsLineLength = width >= maxWidth
            if (exceedsLineLength) {
                val mayUseFullLine = maxWidth == maxLineWidth
                val canFitInLine = fullWidth <= maxLineWidth
                val matchesFullLine = fullWidth == maxLineWidth
                if (lastBreak >= 0) {
                    return lastBreak + 1 // Can do a soft split.
                }
                if (mayUseFullLine && matchesFullLine) {
                    return s.length // Special case for exact match.
                }
                if (canFitInLine && !mayUseFullLine) {
                    return 0 // Wrap line, use next line.
                }
                return pos - 1 // Gotta split hard.
            }
            if (pos < s.length && breaks.contains(s[pos])) lastBreak = pos
        }
        return pos
    }

    fun computeWrapIndent(renderer: FontRenderer): Int =
        if (lists.contains(rootPrefix)) renderer.getStringWidth(rootPrefix) else 0
}
