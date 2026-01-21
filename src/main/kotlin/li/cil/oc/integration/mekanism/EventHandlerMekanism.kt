package li.cil.oc.integration.mekanism

import mekanism.api.IMekWrench
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos

object EventHandlerMekanism {
    @JvmStatic
    fun useWrench(player: EntityPlayer, pos: BlockPos, changeDurability: Boolean): Boolean {
        return when (val item = player.getHeldItem(EnumHand.MAIN_HAND).item) {
            is IMekWrench -> item.canUseWrench(player.getHeldItem(EnumHand.MAIN_HAND), player, pos)
            else -> false
        }
    }

    @JvmStatic
    fun isWrench(stack: ItemStack): Boolean = stack.item is IMekWrench
}
