package li.cil.oc.integration.cofh.item

import cofh.api.item.IToolHammer
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

object EventHandlerCoFH {
    @JvmStatic
    fun useWrench(player: EntityPlayer, pos: BlockPos, changeDurability: Boolean): Boolean {
        val item = player.heldItemMainhand.item
        if (item is IToolHammer) {
            return if (changeDurability) {
                item.toolUsed(player.heldItemMainhand, player, pos)
                true
            } else {
                item.isUsable(player.heldItemMainhand, player, pos)
            }
        }
        return false
    }

    @JvmStatic
    fun isWrench(stack: ItemStack): Boolean = stack.item is IToolHammer
}
