package li.cil.oc.client.renderer.markdown.segment

import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.api
import li.cil.oc.client.Manual
import li.cil.oc.client.renderer.markdown.MarkupFormat
import net.minecraft.client.Minecraft
import java.net.URI

internal class LinkSegment(parent: Segment?, text: String, val url: String) : TextSegment(parent, text), InteractiveSegment {
    private val normalColor = 0x66FF66
    private val normalHoverColor = 0xAAFFAA
    private val errorColor = 0xFF6666
    private val errorHoverColor = 0xFFAAAA
    private val fadeTime = 500
    private val isLinkValid by lazy {
        (url.startsWith("http://") || url.startsWith("https://")) ||
            api.Manual.contentFor(Manual.makeRelative(url, Manual.history.top.path)) != null
    }

    private var lastHovered = System.currentTimeMillis() - fadeTime

    override val color: Int
        get() {
            val (color, hoverColor) = if (isLinkValid) normalColor to normalHoverColor else errorColor to errorHoverColor
            val timeSinceHover = (System.currentTimeMillis() - lastHovered).toInt()
            return if (timeSinceHover > fadeTime) color
            else fadeColor(hoverColor, color, timeSinceHover / fadeTime.toFloat())
        }

    override val tooltip: String
        get() = url

    override fun onMouseClick(mouseX: Int, mouseY: Int): Boolean {
        if (url.startsWith("http://") || url.startsWith("https://")) handleUrl(url)
        else Manual.navigate(Manual.makeRelative(url, Manual.history.top.path))
        return true
    }

    override fun notifyHover() {
        lastHovered = System.currentTimeMillis()
    }

    private fun fadeColor(c1: Int, c2: Int, t: Float): Int {
        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 ushr 16) and 0xFF
        val g2 = (c2 ushr 8) and 0xFF
        val b2 = c2 and 0xFF
        val r = (r1 + (r2 - r1) * t).toInt()
        val g = (g1 + (g2 - g1) * t).toInt()
        val b = (b1 + (b2 - b1) * t).toInt()
        return (r shl 16) or (g shl 8) or b
    }

    private fun handleUrl(url: String) {
        // Pretty much copy-paste from GuiChat.
        try {
            val desktop = Class.forName("java.awt.Desktop")
            val instance = desktop.getMethod("getDesktop").invoke(null)
            desktop.getMethod("browse", URI::class.java).invoke(instance, URI(url))
        } catch (t: Throwable) {
            Minecraft.getMinecraft().player.sendMessage(Localization.Chat.WarningLink(t.toString()))
        }
    }

    override fun toString(format: MarkupFormat): String = when (format) {
        MarkupFormat.Markdown -> "[$text]($url)"
        MarkupFormat.IGWMod ->
            if (url.startsWith("http://") || url.startsWith("https://")) text
            else "[link{${OpenComputers.ID}:$url}]$text [link{}]"
    }
}
