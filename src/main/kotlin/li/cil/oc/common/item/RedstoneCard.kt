package li.cil.oc.common.item

import li.cil.oc.common.Tier
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier
import net.minecraft.item.ItemStack

class RedstoneCard(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier {
    // Note: T2 is enabled in mod integration, if it makes sense.
    init {
        showInItemList = tier == Tier.One
    }

    override fun tooltipExtended(stack: ItemStack, tooltip: MutableList<String>) {
        super<ItemTier>.tooltipExtended(stack, tooltip)
        if (tier == Tier.Two) {
            // TODO Generic system for redstone integration modules to register in a list of tooltip lines.
            //      if (Mods.MOD_NAME.isAvailable) {
            //        tooltip.addAll(Tooltip.get(super.unlocalizedName + ".MOD_NAME"))
            //      }
        }
    }
}
