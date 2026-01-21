package li.cil.oc.integration.railcraft

import mods.railcraft.api.items.IToolCrowbar
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos

object EventHandlerRailcraft {
    @JvmStatic
    fun useWrench(player: EntityPlayer, pos: BlockPos, changeDurability: Boolean): Boolean {
        return when (val item = player.heldItemMainhand.item) {
            is IToolCrowbar -> {
                if (changeDurability) {
                    item.onWhack(player, EnumHand.MAIN_HAND, player.heldItemMainhand, pos)
                    true
                } else {
                    item.canWhack(player, EnumHand.MAIN_HAND, player.heldItemMainhand, pos)
                }
            }
            else -> false
        }
    }

    @JvmStatic
    fun isWrench(stack: ItemStack): Boolean = stack.item is IToolCrowbar
}
