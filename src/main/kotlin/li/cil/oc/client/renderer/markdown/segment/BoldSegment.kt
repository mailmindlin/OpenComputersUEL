package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.util.text.TextFormatting

internal class BoldSegment(parent: Segment?, text: String) : TextSegment(parent, text) {
    override val format: String
        get() = TextFormatting.BOLD.toString()

    override fun toString(format: MarkupFormat): String = when (format) {
        MarkupFormat.Markdown -> "**$text**"
        MarkupFormat.IGWMod -> "[prefix{l}]$text [prefix{}]"
    }
}
