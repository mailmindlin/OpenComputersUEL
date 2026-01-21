package li.cil.oc.common.item

import li.cil.oc.common.Tier
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier
import net.minecraft.item.ItemStack

class RedstoneCard(override val parent: Delegator, val tier: Int) : Delegate, ItemTier {
    override val unlocalizedName: String = super.unlocalizedName + tier

    override val tooltipName: String? get() = super.unlocalizedName

    // Note: T2 is enabled in mod integration, if it makes sense.
    init {
        showInItemList = tier == Tier.One
    }

    override fun tooltipExtended(stack: ItemStack, tooltip: MutableList<String>) {
        super.tooltipExtended(stack, tooltip)
        if (tier == Tier.Two) {
            // TODO Generic system for redstone integration modules to register in a list of tooltip lines.
            //      if (Mods.MOD_NAME.isAvailable) {
            //        tooltip.addAll(Tooltip.get(super.unlocalizedName + ".MOD_NAME"))
            //      }
        }
    }
}
