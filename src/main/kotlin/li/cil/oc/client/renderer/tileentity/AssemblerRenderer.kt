package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Assembler
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.opengl.GL11

object AssemblerRenderer : TileEntitySpecialRenderer<Assembler>() {

    override fun render(assembler: Assembler, x: Double, y: Double, z: Double, f: Float, damage: Int, alpha: Float) {
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        RenderState.pushAttrib()

        RenderState.disableEntityLighting()
        RenderState.makeItBlend()
        RenderState.setBlendAlpha(1.0f)

        GlStateManager.pushMatrix()
        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)

        val t = Tessellator.getInstance()
        val r = t.buffer

        Textures.Block.bind()
        r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)

        run {
            val icon = Textures.getSprite(Textures.Block.AssemblerTopOn)
            r.pos(-0.5, 0.55, 0.5).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
            r.pos(0.5, 0.55, 0.5).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
            r.pos(0.5, 0.55, -0.5).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
            r.pos(-0.5, 0.55, -0.5).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()
        }

        t.draw()

        // TODO Unroll loop to draw all at once?
        val indent = 6 / 16f + 0.005
        for (i in 0 until 4) {
            r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)

            if (assembler.isAssembling) {
                val icon = Textures.getSprite(Textures.Block.AssemblerSideAssembling)
                r.pos(indent.toDouble(), 0.5, (-indent).toDouble()).tex(icon.getInterpolatedU((0.5 - indent) * 16).toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(indent.toDouble(), 0.5, indent.toDouble()).tex(icon.getInterpolatedU((0.5 + indent) * 16).toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(indent.toDouble(), -0.5, indent.toDouble()).tex(icon.getInterpolatedU((0.5 + indent) * 16).toDouble(), icon.minV.toDouble()).endVertex()
                r.pos(indent.toDouble(), -0.5, (-indent).toDouble()).tex(icon.getInterpolatedU((0.5 - indent) * 16).toDouble(), icon.minV.toDouble()).endVertex()
            }

            run {
                val icon = Textures.getSprite(Textures.Block.AssemblerSideOn)
                r.pos(0.5005, 0.5, -0.5).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(0.5005, 0.5, 0.5).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
                r.pos(0.5005, -0.5, 0.5).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
                r.pos(0.5005, -0.5, -0.5).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()
            }

            t.draw()

            GlStateManager.rotate(90f, 0f, 1f, 0f)
        }

        RenderState.disableBlend()
        RenderState.enableEntityLighting()

        GlStateManager.popMatrix()
        RenderState.popAttrib()

        RenderState.checkError(javaClass.name + ".render: leaving")
    }
}
