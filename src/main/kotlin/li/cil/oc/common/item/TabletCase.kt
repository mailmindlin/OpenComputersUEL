package li.cil.oc.common.item

import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier
import net.minecraft.item.ItemStack

class TabletCase(override val parent: Delegator, val tier: Int) : Delegate, ItemTier {
    override val unlocalizedName: String = super.unlocalizedName + tier

    override fun tierFromDriver(stack: ItemStack): Int = tier

    override val tooltipName: String? get() = super.unlocalizedName
}
