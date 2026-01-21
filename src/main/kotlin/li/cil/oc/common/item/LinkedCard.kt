package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier
import li.cil.oc.util.Tooltip
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.world.World

class LinkedCard(override val parent: Delegator) : Delegate, ItemTier {
    override fun tooltipLines(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        if (stack.hasTagCompound() && stack.tagCompound!!.hasKey(Settings.namespace + "data")) {
            val data = stack.tagCompound!!.getCompoundTag(Settings.namespace + "data")
            if (data.hasKey(Settings.namespace + "tunnel")) {
                val channel = data.getString(Settings.namespace + "tunnel")
                if (channel.length > 13) {
                    tooltip.addAll(Tooltip.get(unlocalizedName + "_channel", channel.substring(0, 13) + "..."))
                } else {
                    tooltip.addAll(Tooltip.get(unlocalizedName + "_channel", channel))
                }
            }
        }
        super.tooltipLines(stack, world, tooltip, flag)
    }
}
