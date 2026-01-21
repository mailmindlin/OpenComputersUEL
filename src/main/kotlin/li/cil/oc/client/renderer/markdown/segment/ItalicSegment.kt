package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.util.text.TextFormatting

internal class ItalicSegment(parent: Segment?, text: String) : TextSegment(parent, text) {
    override val format: String
        get() = TextFormatting.ITALIC.toString()

    override fun toString(format: MarkupFormat): String = when (format) {
        MarkupFormat.Markdown -> "*$text*"
        MarkupFormat.IGWMod -> "[prefix{o}]$text [prefix{}]"
    }
}
