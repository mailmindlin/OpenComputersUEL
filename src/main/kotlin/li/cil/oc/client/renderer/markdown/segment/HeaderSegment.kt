package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.util.text.TextFormatting
import kotlin.math.max

internal class HeaderSegment(parent: Segment?, text: String, val level: Int) : TextSegment(parent, text) {
    private val fontScale = max(2, 5 - level) / 2f

    override val scale: Float
        get() = fontScale

    override val format: String
        get() = TextFormatting.UNDERLINE.toString()

    override fun toString(format: MarkupFormat): String = when (format) {
        MarkupFormat.Markdown -> "${"#".repeat(level)} $text"
        MarkupFormat.IGWMod -> "[prefix{l}]$text [prefix{}]"
    }

    override fun toString(): String = "HeaderSegment(level=$level, text=\"$text\")"
}
