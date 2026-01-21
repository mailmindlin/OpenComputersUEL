package li.cil.oc.client.gui.traits

import li.cil.oc.util.OldScaledResolution
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.ResourceLocation

interface Window {
    var guiLeft: Int
    var guiTop: Int
    var xSize: Int
    var ySize: Int

    val windowWidth: Int
        get() = 176

    val windowHeight: Int
        get() = 166

    val backgroundImage: ResourceLocation

    fun <T> add(list: MutableList<T>, value: T) {
        list.add(value)
    }

    fun doesGuiPauseGameWindow(): Boolean = false

    fun initGuiWindow(mc: Minecraft) {
        val screenSize = ScaledResolution(mc)
        val guiSize = OldScaledResolution(mc, windowWidth, windowHeight)
        val midX = screenSize.scaledWidth / 2
        val midY = screenSize.scaledHeight / 2
        guiLeft = midX - guiSize.scaledWidth / 2
        guiTop = midY - guiSize.scaledHeight / 2
        xSize = guiSize.scaledWidth
        ySize = guiSize.scaledHeight
    }

    fun drawScreenWindow(mc: Minecraft, mouseX: Int, mouseY: Int, dt: Float) {
        mc.renderEngine.bindTexture(backgroundImage)
        Gui.drawModalRectWithCustomSizedTexture(guiLeft, guiTop, 0f, 0f, xSize, ySize, windowWidth.toFloat(), windowHeight.toFloat())
    }
}
