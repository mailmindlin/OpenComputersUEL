package li.cil.oc.client.gui.traits

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen

internal val AsGuiScreen.mc: Minecraft get() = asGuiScreen().mc

internal interface AsGuiScreen {
    fun asGuiScreen(): GuiScreen
}