package li.cil.oc.client.renderer.tileentity

import li.cil.oc.client.Textures
import li.cil.oc.common.tileentity.Printer
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.block.model.ItemCameraTransforms
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer

object PrinterRenderer : TileEntitySpecialRenderer<Printer>() {
    override fun render(printer: Printer, x: Double, y: Double, z: Double, f: Float, damage: Int, alpha: Float) {
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        if (printer.data.stateOff.isNotEmpty()) {
            val stack = printer.data.createItemStack()

            RenderState.pushAttrib()
            GlStateManager.pushMatrix()

            GlStateManager.translate(x + 0.5, y + 0.5 + 0.3, z + 0.5)

            GlStateManager.rotate((System.currentTimeMillis() % 20000) / 20000f * 360, 0f, 1f, 0f)
            GlStateManager.scale(0.75, 0.75, 0.75)

            val brightness = printer.world.getCombinedLight(printer.pos, 0)
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (brightness % 65536).toFloat(), (brightness / 65536).toFloat())

            Textures.Block.bind()
            Minecraft.getMinecraft().renderItem.renderItem(stack, ItemCameraTransforms.TransformType.FIXED)

            GlStateManager.popMatrix()
            RenderState.popAttrib()
        }

        RenderState.checkError(javaClass.name + ".render: leaving")
    }
}
