package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.api.manual.ImageRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.item.ItemStack

internal class ItemStackImageRenderer(val stacks: Array<ItemStack>) : ImageRenderer {
    companion object {
        // How long to show individual stacks, in milliseconds, before switching to the next.
        const val CYCLE_SPEED = 1000
    }

    override fun getWidth(): Int = 32

    override fun getHeight(): Int = 32

    override fun render(mouseX: Int, mouseY: Int) {
        val mc = Minecraft.getMinecraft()
        val index = (System.currentTimeMillis() % (CYCLE_SPEED * stacks.size)).toInt() / CYCLE_SPEED
        val stack = stacks[index]

        GlStateManager.scale(getWidth() / 16.0, getHeight() / 16.0, getWidth() / 16.0)
        GlStateManager.enableRescaleNormal()
        RenderHelper.enableGUIStandardItemLighting()
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f)
        mc.renderItem.renderItemAndEffectIntoGUI(stack, 0, 0)
        RenderHelper.disableStandardItemLighting()
    }
}
