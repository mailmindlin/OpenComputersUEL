package li.cil.oc.client.gui.traits

import li.cil.oc.client.renderer.gui.BufferRenderer
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager

/**
 * A trait for GUI components that display a text buffer (e.g., screens, terminals).
 *
 * This interface provides common functionality for rendering text buffers in OpenComputers GUIs,
 * including state management, buffer positioning, and rendering coordination.
 */
internal interface DisplayBuffer: AsGuiScreen {
    /** The X position of the buffer within the GUI. */
    val bufferX: Int

    /** The Y position of the buffer within the GUI. */
    val bufferY: Int

    /** The number of columns (characters per row) in the buffer. */
    val bufferColumns: Int

    /** The number of rows (lines) in the buffer. */
    val bufferRows: Int

    /** The current state of the display buffer, tracking size and scaling information. */
    var displayBufferState: State

    /**
     * Initializes the GUI and buffer renderer.
     *
     * This method should be called when the GUI is first opened or needs to be reinitialized.
     * It sets up the BufferRenderer and marks the GUI size as changed.
     */
    fun initGui() {
        BufferRenderer.init(Minecraft.getMinecraft().renderEngine)
        displayBufferState.guiSizeChanged = true
    }

    /**
     * Renders the buffer layer with proper OpenGL state management.
     *
     * This method handles:
     * - Tracking buffer dimension changes
     * - Calling [changeSize] when dimensions change
     * - Managing OpenGL matrix stack and lighting state
     * - Delegating actual rendering to [drawBuffer]
     * - Error checking before and after rendering
     */
    fun drawBufferLayer() {
        val displayBufferState = displayBufferState
        val oldWidth = displayBufferState.currentWidth
        val oldHeight = displayBufferState.currentHeight
        displayBufferState.currentWidth = bufferColumns
        displayBufferState.currentHeight = bufferRows
        displayBufferState.scale = changeSize(displayBufferState.currentWidth, displayBufferState.currentHeight,
            displayBufferState.guiSizeChanged || oldWidth != displayBufferState.currentWidth || oldHeight != displayBufferState.currentHeight)

        RenderState.checkError(this.javaClass.name + ".drawBufferLayer: entering (aka: wasntme)")

        GlStateManager.pushMatrix()
        RenderState.disableEntityLighting()
        drawBuffer()
        GlStateManager.popMatrix()

        RenderState.checkError(this.javaClass.name + ".drawBufferLayer: buffer layer")
    }

    /**
     * Renders the actual buffer content.
     *
     * Implementations should draw the text buffer contents at this point.
     * The OpenGL state (matrix, lighting) is already set up by [drawBufferLayer].
     */
    fun drawBuffer()

    /**
     * Handles buffer size changes and calculates the appropriate scale factor.
     *
     * @param w The new width in columns
     * @param h The new height in rows
     * @param recompile Whether the buffer needs to be recompiled (true if size changed or GUI was resized)
     * @return The scale factor to apply for rendering the buffer
     */
    fun changeSize(w: Int, h: Int, recompile: Boolean): Double

    /**
     * State container for the display buffer.
     *
     * @property guiSizeChanged Whether the GUI size has changed since last render
     * @property currentWidth The current buffer width in columns
     * @property currentHeight The current buffer height in rows
     * @property scale The current scale factor for rendering
     */
    data class State(
        internal var guiSizeChanged: Boolean = false,
        internal var currentWidth: Int = -1,
        internal var currentHeight: Int = -1,
        internal var scale: Double = 0.0,
    )
}
