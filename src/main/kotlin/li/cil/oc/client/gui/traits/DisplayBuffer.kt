package li.cil.oc.client.gui.traits

import li.cil.oc.client.renderer.gui.BufferRenderer
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager

interface DisplayBuffer {
    val bufferX: Int

    val bufferY: Int

    val bufferColumns: Int

    val bufferRows: Int

    var displayBufferState: State

    fun initGui() {
        BufferRenderer.init(Minecraft.getMinecraft().renderEngine)
        displayBufferState.guiSizeChanged = true
    }

    fun drawBufferLayer() {
        val displayBufferState = displayBufferState
        val oldWidth = displayBufferState.currentWidth
        val oldHeight = displayBufferState.currentHeight
        displayBufferState.currentWidth = bufferColumns
        displayBufferState.currentHeight = bufferRows
        displayBufferState.scale = changeSize(displayBufferState.currentWidth.toDouble(), displayBufferState.currentHeight.toDouble(),
            displayBufferState.guiSizeChanged || oldWidth != displayBufferState.currentWidth || oldHeight != displayBufferState.currentHeight)

        RenderState.checkError(this.javaClass.name + ".drawBufferLayer: entering (aka: wasntme)")

        GlStateManager.pushMatrix()
        RenderState.disableEntityLighting()
        drawBuffer()
        GlStateManager.popMatrix()

        RenderState.checkError(this.javaClass.name + ".drawBufferLayer: buffer layer")
    }

    fun drawBuffer()

    fun changeSize(w: Double, h: Double, recompile: Boolean): Double

    data class State(
        internal var guiSizeChanged: Boolean = false,
        internal var currentWidth: Int = -1,
        internal var currentHeight: Int = -1,
        internal var scale: Double = 0.0,
    )
}
