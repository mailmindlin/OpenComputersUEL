package li.cil.oc.integration.projectred

import mrtjp.projectred.api.IScrewdriver
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

object EventHandlerProjectRed {
    @JvmStatic
    fun useWrench(player: EntityPlayer, pos: BlockPos, changeDurability: Boolean): Boolean {
        val stack = player.heldItemMainhand
        return when (val item = stack.item) {
            is IScrewdriver -> {
                if (changeDurability) {
                    item.damageScrewdriver(player, stack)
                    true
                } else {
                    true
                }
            }
            else -> false
        }
    }

    @JvmStatic
    fun isWrench(stack: ItemStack): Boolean = stack.item is IScrewdriver
}
