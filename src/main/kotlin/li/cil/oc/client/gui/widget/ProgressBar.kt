package li.cil.oc.client.gui.widget

import li.cil.oc.client.Textures
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.opengl.GL11

open class ProgressBar(override val x: Int, override val y: Int) : Widget() {
    override val width: Int = 140

    override val height: Int = 12

    open val barTexture = Textures.GUI.Bar

    var level = 0.0

    override fun draw() {
        if (level > 0 && owner != null) {
            val u0 = 0.0
            val u1 = level
            val v0 = 0.0
            val v1 = 1.0
            val tx = owner!!.windowX + x
            val ty = owner!!.windowY + y
            val w = width * level

            Textures.bind(barTexture)
            val t = Tessellator.getInstance()
            val r = t.buffer
            r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)
            r.pos(tx.toDouble(), ty.toDouble(), owner!!.windowZ.toDouble()).tex(u0, v0).endVertex()
            r.pos(tx.toDouble(), (ty + height).toDouble(), owner!!.windowZ.toDouble()).tex(u0, v1).endVertex()
            r.pos((tx + w).toDouble(), (ty + height).toDouble(), owner!!.windowZ.toDouble()).tex(u1, v1).endVertex()
            r.pos((tx + w).toDouble(), ty.toDouble(), owner!!.windowZ.toDouble()).tex(u1, v0).endVertex()
            t.draw()
        }
    }
}
