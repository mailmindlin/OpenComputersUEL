package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Charger
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import org.lwjgl.opengl.GL11

object ChargerRenderer : TileEntitySpecialRenderer<Charger>() {
    override fun render(charger: Charger, x: Double, y: Double, z: Double, f: Float, damage: Int, alpha: Float) {
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        if (charger.chargeSpeed > 0) {
            RenderState.pushAttrib()

            RenderState.disableEntityLighting()
            RenderState.makeItBlend()
            RenderState.setBlendAlpha(1.0f)
            GlStateManager.color(1f, 1f, 1f, 1f)

            GlStateManager.pushMatrix()

            GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)

            when (charger.yaw) {
                EnumFacing.WEST -> GlStateManager.rotate(-90f, 0f, 1f, 0f)
                EnumFacing.NORTH -> GlStateManager.rotate(180f, 0f, 1f, 0f)
                EnumFacing.EAST -> GlStateManager.rotate(90f, 0f, 1f, 0f)
                else -> {} // No yaw.
            }

            GlStateManager.translate(-0.5f, 0.5f, 0.5f)
            GlStateManager.scale(1.0, -1.0, 1.0)

            val t = Tessellator.getInstance()
            val r = t.buffer

            Textures.Block.bind()
            r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)

            run {
                val inverse = 1 - charger.chargeSpeed
                val icon = Textures.getSprite(Textures.Block.ChargerFrontOn)
                r.pos(0.0, 1.0, 0.005).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(1.0, 1.0, 0.005).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(1.0, inverse.toDouble(), 0.005).tex(icon.maxU.toDouble(), icon.getInterpolatedV(inverse * 16).toDouble()).endVertex()
                r.pos(0.0, inverse.toDouble(), 0.005).tex(icon.minU.toDouble(), icon.getInterpolatedV(inverse * 16).toDouble()).endVertex()
            }

            if (charger.hasPower) {
                val icon = Textures.getSprite(Textures.Block.ChargerSideOn)

                r.pos(-0.005, 1.0, -1.0).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(-0.005, 1.0, 0.0).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(-0.005, 0.0, 0.0).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
                r.pos(-0.005, 0.0, -1.0).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()

                r.pos(1.0, 1.0, -1.005).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(0.0, 1.0, -1.005).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(0.0, 0.0, -1.005).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
                r.pos(1.0, 0.0, -1.005).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()

                r.pos(1.005, 1.0, 0.0).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(1.005, 1.0, -1.0).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(1.005, 0.0, -1.0).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
                r.pos(1.005, 0.0, 0.0).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()
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
