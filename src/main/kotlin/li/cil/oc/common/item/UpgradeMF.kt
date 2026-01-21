package li.cil.oc.common.item

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier
import li.cil.oc.util.BlockPosition
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing

class UpgradeMF(override val parent: Delegator) : Delegate, ItemTier {
    override fun onItemUseFirst(stack: ItemStack, player: EntityPlayer, position: BlockPosition, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): EnumActionResult {
        if (!player.world.isRemote && player.isSneaking) {
            if (!stack.hasTagCompound()) {
                stack.tagCompound = NBTTagCompound()
            }
            val data = stack.tagCompound!!
            data.setIntArray(Settings.namespace + "coord", intArrayOf(position.x, position.y, position.z, player.world.provider.dimension, side.ordinal))
            return EnumActionResult.SUCCESS
        }
        return super.onItemUseFirst(stack, player, position, side, hitX, hitY, hitZ)
    }

    override fun tooltipExtended(stack: ItemStack, tooltip: MutableList<String>) {
        val hasCoord = stack.tagCompound?.hasKey(Settings.namespace + "coord") ?: false
        tooltip.add(Localization.Tooltip.MFULinked(hasCoord))
    }
}
