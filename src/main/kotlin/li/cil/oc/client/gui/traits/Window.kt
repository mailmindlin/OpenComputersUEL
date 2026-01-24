package li.cil.oc.client.gui.traits

import li.cil.oc.util.OldScaledResolution
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.ResourceLocation

interface Window {
    val windowState: State

    val windowWidth: Int
        get() = 176

    val windowHeight: Int
        get() = 166

    val backgroundImage: ResourceLocation

    fun <T> add(list: MutableList<T>, value: T) {
        list.add(value)
    }

    fun initGuiWindow(mc: Minecraft) {
        val screenSize = ScaledResolution(mc)
        val guiSize = OldScaledResolution(mc, windowWidth, windowHeight)
        val midX = screenSize.scaledWidth / 2
        val midY = screenSize.scaledHeight / 2
        val state = windowState
        state.guiLeft = midX - guiSize.scaledWidth / 2
        state.guiTop = midY - guiSize.scaledHeight / 2
        state.xSize = guiSize.scaledWidth
        state.ySize = guiSize.scaledHeight
    }

    fun drawScreenWindow(mc: Minecraft, mouseX: Int, mouseY: Int, dt: Float) {
        mc.renderEngine.bindTexture(backgroundImage)
        val state = windowState
        Gui.drawModalRectWithCustomSizedTexture(state.guiLeft, state.guiTop, 0f, 0f, state.xSize, state.ySize, windowWidth.toFloat(), windowHeight.toFloat())
    }

    @Suppress("DataClassPrivateConstructor")
    data class State private constructor(
        internal var guiLeft: Int = 0,
        internal var guiTop: Int = 0,
        internal var xSize: Int = 0,
        internal var ySize: Int = 0,
    ) {
        constructor(): this(0, 0, 0, 0)
    }
}
