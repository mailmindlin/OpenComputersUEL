package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.Tier
import li.cil.oc.common.item.traits.ItemTier
import li.cil.oc.util.Rarity
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemStack

class ComponentBus(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier {
    // Because the driver considers the creative bus to be tier 3, the superclass
    // will believe it has T3 rarity. We override that here.
    override fun rarity(stack: ItemStack): EnumRarity =
        if (tier == Tier.Four) Rarity.byTier(Tier.Four)
        else super<AbstractTieredDelegate>.rarity(stack)

    override val tooltipData: Array<Any> get() = arrayOf(Settings.get.cpuComponentSupport[tier])
}
