package li.cil.oc.client.renderer.tileentity

import li.cil.oc.common.tileentity.Hologram
import li.cil.oc.util.RenderState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer

object HologramRendererFallback : TileEntitySpecialRenderer<Hologram>() {
    var text = "Requires OpenGL 1.5"

    override fun render(hologram: Hologram, x: Double, y: Double, z: Double, f: Float, damage: Int, alpha: Float) {
        RenderState.checkError(javaClass.name + ".render: entering (aka: wasntme)")

        val fontRenderer = Minecraft.getMinecraft().fontRenderer

        GlStateManager.pushMatrix()
        GlStateManager.translate(x + 0.5, y + 0.75, z + 0.5)

        GlStateManager.scale(1 / 128f, -1 / 128f, 1 / 128f)
        GlStateManager.disableCull()
        fontRenderer.drawString(text, -fontRenderer.getStringWidth(text) / 2, 0, 0xFFFFFFFF.toInt())

        GlStateManager.popMatrix()

        RenderState.checkError(javaClass.name + ".render: leaving")
    }
}
