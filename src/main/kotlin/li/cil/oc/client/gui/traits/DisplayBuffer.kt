package li.cil.oc.client.gui.traits

import li.cil.oc.client.renderer.gui.BufferRenderer
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.GlStateManager

interface DisplayBuffer {
    val bufferX: Int

    val bufferY: Int

    val bufferColumns: Int

    val bufferRows: Int

    var guiSizeChanged: Boolean

    var currentWidth: Int
    var currentHeight: Int

    var scale: Double

    fun initGui(screen: GuiScreen) {
        BufferRenderer.init(Minecraft.getMinecraft().renderEngine)
        guiSizeChanged = true
    }

    fun drawBufferLayer(screen: GuiScreen) {
        val oldWidth = currentWidth
        val oldHeight = currentHeight
        currentWidth = bufferColumns
        currentHeight = bufferRows
        scale = changeSize(currentWidth.toDouble(), currentHeight.toDouble(),
            guiSizeChanged || oldWidth != currentWidth || oldHeight != currentHeight)

        RenderState.checkError(screen.javaClass.name + ".drawBufferLayer: entering (aka: wasntme)")

        GlStateManager.pushMatrix()
        RenderState.disableEntityLighting()
        drawBuffer()
        GlStateManager.popMatrix()

        RenderState.checkError(screen.javaClass.name + ".drawBufferLayer: buffer layer")
    }

    fun drawBuffer()

    fun changeSize(w: Double, h: Double, recompile: Boolean): Double
}
