package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.PowerDistributor
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.opengl.GL11

object PowerDistributorRenderer : TileEntitySpecialRenderer<PowerDistributor>() {
    override fun render(distributor: PowerDistributor, x: Double, y: Double, z: Double, f: Float, damage: Int, alpha: Float) {
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        if (distributor.globalBuffer > 0) {
            RenderState.pushAttrib()

            RenderState.disableEntityLighting()
            RenderState.makeItBlend()
            RenderState.setBlendAlpha((distributor.globalBuffer / distributor.globalBufferSize).toFloat())

            GlStateManager.pushMatrix()

            GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)
            GlStateManager.scale(1.0025, -1.0025, 1.0025)
            GlStateManager.translate(-0.5f, -0.5f, -0.5f)

            val t = Tessellator.getInstance()
            val r = t.buffer

            Textures.Block.bind()
            r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)

            run {
                val icon = Textures.getSprite(Textures.Block.PowerDistributorTopOn)
                r.pos(0.0, 0.0, 1.0).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(1.0, 0.0, 1.0).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(1.0, 0.0, 0.0).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
                r.pos(0.0, 0.0, 0.0).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()
            }

            run {
                val icon = Textures.getSprite(Textures.Block.PowerDistributorSideOn)
                r.pos(1.0, 1.0, 0.0).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(0.0, 1.0, 0.0).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(0.0, 0.0, 0.0).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
                r.pos(1.0, 0.0, 0.0).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()

                r.pos(0.0, 1.0, 1.0).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(1.0, 1.0, 1.0).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(1.0, 0.0, 1.0).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
                r.pos(0.0, 0.0, 1.0).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()

                r.pos(1.0, 1.0, 1.0).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(1.0, 1.0, 0.0).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(1.0, 0.0, 0.0).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
                r.pos(1.0, 0.0, 1.0).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()

                r.pos(0.0, 1.0, 0.0).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(0.0, 1.0, 1.0).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(0.0, 0.0, 1.0).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
                r.pos(0.0, 0.0, 0.0).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()
            }

            t.draw()

            RenderState.disableBlend()
            RenderState.enableEntityLighting()

            GlStateManager.popMatrix()
            RenderState.popAttrib()
        }

        RenderState.checkError(javaClass.name + ".render: leaving")
    }

}
