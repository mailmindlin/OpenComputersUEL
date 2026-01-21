package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.util.text.TextFormatting

internal class StrikethroughSegment(parent: Segment?, text: String) : TextSegment(parent, text) {
    override val format: String
        get() = TextFormatting.STRIKETHROUGH.toString()

    override fun toString(format: MarkupFormat): String = when (format) {
        MarkupFormat.Markdown -> "~~$text~~"
        MarkupFormat.IGWMod -> "[prefix{m}]$text [prefix{}]"
    }
}
