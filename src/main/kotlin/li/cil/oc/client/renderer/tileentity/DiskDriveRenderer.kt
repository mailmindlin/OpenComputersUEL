package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.DiskDrive
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.block.model.ItemCameraTransforms
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.entity.item.EntityItem
import net.minecraft.util.EnumFacing
import org.lwjgl.opengl.GL11

object DiskDriveRenderer : TileEntitySpecialRenderer<DiskDrive>() {
    override fun render(drive: DiskDrive, x: Double, y: Double, z: Double, f: Float, damage: Int, alpha: Float) {
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        RenderState.pushAttrib()
        GlStateManager.color(1f, 1f, 1f, 1f)

        GlStateManager.pushMatrix()

        GlStateManager.translate(x + 0.5, y + 0.5, z + 0.5)

        when (drive.yaw ?: EnumFacing.SOUTH) {
            EnumFacing.WEST -> GlStateManager.rotate(-90f, 0f, 1f, 0f)
            EnumFacing.NORTH -> GlStateManager.rotate(180f, 0f, 1f, 0f)
            EnumFacing.EAST -> GlStateManager.rotate(90f, 0f, 1f, 0f)
            else -> {} // No yaw.
        }

        val stack = drive.items[0]
        if (!stack.isEmpty) {
            GlStateManager.pushMatrix()
            GlStateManager.translate(0f, 3.5f / 16f, 6f / 16f)
            GlStateManager.rotate(90f, -1f, 0f, 0f)
            GlStateManager.scale(0.5f, 0.5f, 0.5f)

            val brightness = drive.world.getCombinedLight(drive.pos.offset(drive.facing() ?: EnumFacing.SOUTH), 0)
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (brightness % 65536).toFloat(), (brightness / 65536).toFloat())

            // This is very 'meh', but item frames do it like this, too!
            val entity = EntityItem(drive.world, 0.0, 0.0, 0.0, stack)
            entity.hoverStart = 0f
            Textures.Block.bind()
            Minecraft.getMinecraft().renderItem.renderItem(entity.item, ItemCameraTransforms.TransformType.FIXED)
            GlStateManager.popMatrix()
        }

        if (System.currentTimeMillis() - drive.lastAccess < 400 && drive.world.rand.nextDouble() > 0.1) {
            GlStateManager.translate(-0.5, 0.5, 0.505)
            GlStateManager.scale(1.0, -1.0, 1.0)

            RenderState.disableEntityLighting()
            RenderState.makeItBlend()
            RenderState.setBlendAlpha(1.0f)

            val t = Tessellator.getInstance()
            val r = t.buffer

            Textures.Block.bind()
            r.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX)

            val icon = Textures.getSprite(Textures.Block.DiskDriveFrontActivity)
            r.pos(0.0, 1.0, 0.0).tex(icon.minU.toDouble(), icon.maxV.toDouble()).endVertex()
            r.pos(1.0, 1.0, 0.0).tex(icon.maxU.toDouble(), icon.maxV.toDouble()).endVertex()
            r.pos(1.0, 0.0, 0.0).tex(icon.maxU.toDouble(), icon.minV.toDouble()).endVertex()
            r.pos(0.0, 0.0, 0.0).tex(icon.minU.toDouble(), icon.minV.toDouble()).endVertex()

            t.draw()

            RenderState.disableBlend()
            RenderState.enableEntityLighting()
        }

        GlStateManager.popMatrix()
        RenderState.popAttrib()

        RenderState.checkError(javaClass.name + ".render: leaving")
    }
}
