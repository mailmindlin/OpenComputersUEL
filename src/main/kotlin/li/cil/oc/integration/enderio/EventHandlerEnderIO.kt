package li.cil.oc.integration.enderio

import crazypants.enderio.api.tool.ITool
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos

object EventHandlerEnderIO {
    @JvmStatic
    fun useWrench(player: EntityPlayer, pos: BlockPos, changeDurability: Boolean): Boolean {
        val item = player.heldItemMainhand.item
        if (item is ITool) {
            return if (changeDurability) {
                item.used(EnumHand.MAIN_HAND, player, pos)
                true
            } else {
                item.canUse(EnumHand.MAIN_HAND, player, pos)
            }
        }
        return false
    }

    @JvmStatic
    fun isWrench(stack: ItemStack): Boolean = stack.item is ITool
}
