package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Case
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

object CaseRenderer : TileEntitySpecialRenderer<Case>() {
    override fun render(computer: Case, x: Double, y: Double, z: Double, f: Float, damage: Int, alpha: Float) {
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        RenderState.pushAttrib()

        RenderState.disableEntityLighting()
        RenderState.makeItBlend()
        RenderState.setBlendAlpha(1.0f)

        GlStateManager.pushMatrix()

        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)

        when (computer.yaw ?: EnumFacing.SOUTH) {
            EnumFacing.WEST -> GlStateManager.rotate(-90f, 0f, 1f, 0f)
            EnumFacing.NORTH -> GlStateManager.rotate(180f, 0f, 1f, 0f)
            EnumFacing.EAST -> GlStateManager.rotate(90f, 0f, 1f, 0f)
            else -> {} // No yaw.
        }

        GlStateManager.translate(-0.5, 0.5, 0.505)
        GlStateManager.scale(1.0, -1.0, 1.0)

        if (computer.isRunning) {
            renderFrontOverlay(Textures.Block.CaseFrontOn)
            if (System.currentTimeMillis() - computer.lastFileSystemAccess < 400 && computer.world.rand.nextDouble() > 0.1) {
                renderFrontOverlay(Textures.Block.CaseFrontActivity)
            }
        } else if (computer.hasErrored && RenderUtil.shouldShowErrorLight(computer.hashCode())) {
            renderFrontOverlay(Textures.Block.CaseFrontError)
        }

        RenderState.disableBlend()
        RenderState.enableEntityLighting()

        GlStateManager.popMatrix()
        RenderState.popAttrib()

        RenderState.checkError(javaClass.name + ".render: leaving")
    }

    private fun renderFrontOverlay(texture: ResourceLocation) {
        val t = Tessellator.getInstance()
        val r = t.buffer

        Textures.Block.bind()
        r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)

        val icon = Textures.getSprite(texture)
        r.pos(0.0, 1.0, 0.0).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
        r.pos(1.0, 1.0, 0.0).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
        r.pos(1.0, 0.0, 0.0).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
        r.pos(0.0, 0.0, 0.0).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()

        t.draw()
    }
}
