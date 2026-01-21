package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Raid
import li.cil.oc.util.RenderState
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import org.lwjgl.opengl.GL11

object RaidRenderer : TileEntitySpecialRenderer<Raid>() {
    override fun render(raid: Raid, x: Double, y: Double, z: Double, f: Float, damage: Int, alpha: Float) {
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        RenderState.pushAttrib()

        RenderState.disableEntityLighting()
        RenderState.makeItBlend()
        GlStateManager.color(1f, 1f, 1f, 1f)

        GlStateManager.pushMatrix()

        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)

        when (raid.yaw) {
            EnumFacing.WEST -> GlStateManager.rotate(-90f, 0f, 1f, 0f)
            EnumFacing.NORTH -> GlStateManager.rotate(180f, 0f, 1f, 0f)
            EnumFacing.EAST -> GlStateManager.rotate(90f, 0f, 1f, 0f)
            else -> {} // No yaw.
        }

        GlStateManager.translate(-0.5, 0.5, 0.505)
        GlStateManager.scale(1.0, -1.0, 1.0)

        val t = Tessellator.getInstance()
        val r = t.buffer

        Textures.Block.bind()
        r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)

        run {
            val icon = Textures.getSprite(Textures.Block.RaidFrontError)
            for (slot in 0 until raid.sizeInventory) {
                if (!raid.presence[slot]) {
                    renderSlot(r, slot, icon)
                }
            }
        }

        run {
            val icon = Textures.getSprite(Textures.Block.RaidFrontActivity)
            for (slot in 0 until raid.sizeInventory) {
                if (System.currentTimeMillis() - raid.lastAccess < 400 && raid.world.rand.nextDouble() > 0.1 && slot == (raid.lastAccess % raid.sizeInventory).toInt()) {
                    renderSlot(r, slot, icon)
                }
            }
        }

        t.draw()

        RenderState.disableBlend()
        RenderState.enableEntityLighting()

        GlStateManager.popMatrix()
        RenderState.popAttrib()

        RenderState.checkError(javaClass.name + ".render: leaving")
    }

    private const val u1 = 2 / 16f
    private const val fs = 4 / 16f

    private fun renderSlot(r: BufferBuilder, slot: Int, icon: TextureAtlasSprite) {
        val l = u1 + slot * fs
        val h = u1 + (slot + 1) * fs
        r.pos(l.toDouble(), 1.0, 0.0).tex(icon.getInterpolatedU((l * 16).toDouble()), icon.maxV.toDouble()).endVertex()
        r.pos(h.toDouble(), 1.0, 0.0).tex(icon.getInterpolatedU((h * 16).toDouble()), icon.maxV.toDouble()).endVertex()
        r.pos(h.toDouble(), 0.0, 0.0).tex(icon.getInterpolatedU((h * 16).toDouble()), icon.minV.toDouble()).endVertex()
        r.pos(l.toDouble(), 0.0, 0.0).tex(icon.getInterpolatedU((l * 16).toDouble()), icon.minV.toDouble()).endVertex()
    }
}
