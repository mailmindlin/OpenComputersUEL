package li.cil.oc.util

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.client.KeyBindings
import net.minecraft.client.Minecraft

object Tooltip {
    private const val maxWidth = 220

    private val font get() = Minecraft.getMinecraft().fontRenderer

    @JvmStatic
    fun get(name: String, vararg args: Any): MutableList<String> {
        if (!Localization.canLocalize(Settings.namespace + "tooltip." + name)) {
            return mutableListOf()
        }
        val tooltip = Localization.localizeImmediately("tooltip.$name")
            .format(*args.map { it.toString() }.toTypedArray())

        // Some mods request tooltips before font renderer is available.
        if (font == null) {
            return tooltip.lines().toMutableList()
        }

        val isSubTooltip = name.contains(".")
        val shouldShorten = (isSubTooltip || font.getStringWidth(tooltip) > maxWidth) && !KeyBindings.showExtendedTooltips

        return if (shouldShorten) {
            if (isSubTooltip) mutableListOf()
            else mutableListOf(Localization.localizeImmediately("tooltip.toolong", KeyBindings.getKeyBindingName(KeyBindings.extendedTooltip)))
        } else {
            tooltip.lines()
                .flatMap { line ->
                    font.listFormattedStringToWidth(line, maxWidth)
                        .map { (it as String).trim() + " " }
                }
                .toMutableList()
        }
    }

    @JvmStatic
    fun extended(name: String, vararg args: Any): MutableList<String> {
        return if (KeyBindings.showExtendedTooltips) {
            Localization.localizeImmediately("tooltip.$name")
                .format(*args.map { it.toString() }.toTypedArray())
                .lines()
                .flatMap { line ->
                    font.listFormattedStringToWidth(line, maxWidth)
                        .map { (it as String).trim() + " " }
                }
                .toMutableList()
        } else mutableListOf()
    }
}
