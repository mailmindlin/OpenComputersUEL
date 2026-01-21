package li.cil.oc.client.renderer.markdown.segment.render

import com.google.common.base.Strings
import li.cil.oc.api.manual.ImageProvider
import li.cil.oc.api.manual.ImageRenderer
import li.cil.oc.api.manual.InteractiveImageRenderer
import li.cil.oc.client.Textures
import net.minecraft.block.Block
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

object BlockImageProvider : ImageProvider {
    override fun getImage(data: String): ImageRenderer {
        val splitIndex = data.lastIndexOf('@')
        val (name, optMeta) = if (splitIndex > 0) {
            data.substring(0, splitIndex) to data.substring(splitIndex)
        } else {
            data to ""
        }
        val meta = if (Strings.isNullOrEmpty(optMeta)) 0 else Integer.parseInt(optMeta.drop(1))
        val block = Block.REGISTRY.getObject(ResourceLocation(name))
        return if (block is Block && Item.getItemFromBlock(block) != null) {
            ItemStackImageRenderer(arrayOf(ItemStack(block, 1, meta)))
        } else {
            object : TextureImageRenderer(Textures.GUI.ManualMissingItem), InteractiveImageRenderer {
                override fun getTooltip(tooltip: String): String = "oc:gui.Manual.Warning.BlockMissing"

                override fun onMouseClick(mouseX: Int, mouseY: Int): Boolean = false
            }
        }
    }
}
