package li.cil.oc.client.renderer.markdown.segment.render

import li.cil.oc.api.manual.ImageProvider
import li.cil.oc.api.manual.ImageRenderer
import li.cil.oc.api.manual.InteractiveImageRenderer
import li.cil.oc.client.Textures
import net.minecraftforge.oredict.OreDictionary

object OreDictImageProvider : ImageProvider {
    override fun getImage(data: String): ImageRenderer {
        val stacks = OreDictionary.getOres(data).filter { stack -> !stack.isEmpty && stack.item != null }
        return if (stacks.isNotEmpty()) {
            ItemStackImageRenderer(stacks.toTypedArray())
        } else {
            object : TextureImageRenderer(Textures.GUI.ManualMissingItem), InteractiveImageRenderer {
                override fun getTooltip(tooltip: String): String = "oc:gui.Manual.Warning.OreDictMissing"

                override fun onMouseClick(mouseX: Int, mouseY: Int): Boolean = false
            }
        }
    }
}
