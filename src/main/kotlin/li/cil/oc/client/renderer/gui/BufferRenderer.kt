package li.cil.oc.client.renderer.gui

import li.cil.oc.api.internal.TextBuffer
import li.cil.oc.client.Textures
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GLAllocation
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.texture.TextureManager
import org.lwjgl.opengl.GL11

object BufferRenderer {
    const val margin = 7
    const val innerMargin = 1

    private var textureManager: TextureManager? = null
    private var displayLists = 0

    @Synchronized
    fun init(tm: TextureManager) {
        if (textureManager == null) {
            RenderState.checkError(javaClass.name + ".displayLists: entering (aka: wasntme)")

            textureManager = tm
            displayLists = GLAllocation.generateDisplayLists(2)

            RenderState.checkError(javaClass.name + ".displayLists: leaving")
        }
    }

    fun compileBackground(bufferWidth: Int, bufferHeight: Int, forRobot: Boolean = false) {
        if (textureManager != null) {
            RenderState.checkError(javaClass.name + ".compileBackground: entering (aka: wasntme)")

            val innerWidth = innerMargin * 2 + bufferWidth
            val innerHeight = innerMargin * 2 + bufferHeight

            GL11.glNewList(displayLists, GL11.GL_COMPILE)

            Textures.bind(Textures.GUI.Borders)

            GL11.glBegin(GL11.GL_QUADS)

            val margin = if (forRobot) 2 else 7
            val (c0, c1, c2, c3) = if (forRobot) listOf(5, 7, 9, 11) else listOf(0, 7, 9, 16)

            // Top border (left corner, middle bar, right corner).
            drawBorder(
                0.0, 0.0, margin.toDouble(), margin.toDouble(),
                c0.toDouble(), c0.toDouble(), c1.toDouble(), c1.toDouble()
            )
            drawBorder(
                margin.toDouble(), 0.0, innerWidth.toDouble(), margin.toDouble(),
                c1 + 0.25, c0.toDouble(), c2 - 0.25, c1.toDouble()
            )
            drawBorder(
                (margin + innerWidth).toDouble(), 0.0, margin.toDouble(), margin.toDouble(),
                c2.toDouble(), c0.toDouble(), c3.toDouble(), c1.toDouble()
            )

            // Middle area (left bar, screen background, right bar).
            drawBorder(
                0.0, margin.toDouble(), margin.toDouble(), innerHeight.toDouble(),
                c0.toDouble(), c1 + 0.25, c1.toDouble(), c2 - 0.25
            )
            drawBorder(
                margin.toDouble(), margin.toDouble(), innerWidth.toDouble(), innerHeight.toDouble(),
                c1 + 0.25, c1 + 0.25, c2 - 0.25, c2 - 0.25
            )
            drawBorder(
                (margin + innerWidth).toDouble(), margin.toDouble(), margin.toDouble(), innerHeight.toDouble(),
                c2.toDouble(), c1 + 0.25, c3.toDouble(), c2 - 0.25
            )

            // Bottom border (left corner, middle bar, right corner).
            drawBorder(
                0.0, (margin + innerHeight).toDouble(), margin.toDouble(), margin.toDouble(),
                c0.toDouble(), c2.toDouble(), c1.toDouble(), c3.toDouble()
            )
            drawBorder(
                margin.toDouble(), (margin + innerHeight).toDouble(), innerWidth.toDouble(), margin.toDouble(),
                c1 + 0.25, c2.toDouble(), c2 - 0.25, c3.toDouble()
            )
            drawBorder(
                (margin + innerWidth).toDouble(), (margin + innerHeight).toDouble(), margin.toDouble(), margin.toDouble(),
                c2.toDouble(), c2.toDouble(), c3.toDouble(), c3.toDouble()
            )

            GL11.glEnd()

            GL11.glEndList()

            RenderState.checkError(javaClass.name + ".compileBackground: leaving")
        }
    }

    fun drawBackground() {
        if (textureManager != null) {
            GL11.glCallList(displayLists)
        }
    }

    fun drawText(screen: TextBuffer): Boolean {
        return if (textureManager != null) {
            RenderState.pushAttrib()
            GlStateManager.depthMask(false)
            val changed = screen.renderText()
            GlStateManager.depthMask(true)
            RenderState.popAttrib()
            changed
        } else false
    }

    private fun drawBorder(x: Double, y: Double, w: Double, h: Double, u1: Double, v1: Double, u2: Double, v2: Double) {
        val u1d = u1 / 16.0
        val u2d = u2 / 16.0
        val v1d = v1 / 16.0
        val v2d = v2 / 16.0
        GL11.glTexCoord2d(u1d, v2d)
        GL11.glVertex3d(x, y + h, 0.0)
        GL11.glTexCoord2d(u2d, v2d)
        GL11.glVertex3d(x + w, y + h, 0.0)
        GL11.glTexCoord2d(u2d, v1d)
        GL11.glVertex3d(x + w, y, 0.0)
        GL11.glTexCoord2d(u1d, v1d)
        GL11.glVertex3d(x, y, 0.0)
    }
}
