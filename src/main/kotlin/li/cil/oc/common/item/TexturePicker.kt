package li.cil.oc.common.item

import li.cil.oc.Localization
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.getBlock
import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing

class TexturePicker(parent: Delegator) : AbstractDelegate(parent) {
    override fun onItemUse(stack: ItemStack, player: EntityPlayer, position: BlockPosition, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        val world = player.entityWorld
        val block = world.getBlock(position) ?: return super.onItemUse(stack, player, position, side, hitX, hitY, hitZ)
        if (world.isRemote) {
            val model = Minecraft.getMinecraft().blockRendererDispatcher.getModelForState(world.getBlockState(position.toBlockPos()))
            if (model?.particleTexture?.iconName != null) {
                player.sendMessage(Localization.Chat.TextureName(model.particleTexture.iconName))
            }
        }
        return true
    }
}
