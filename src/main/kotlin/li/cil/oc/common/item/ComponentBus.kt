package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.Tier
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier
import li.cil.oc.util.Rarity
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemStack

class ComponentBus(override val parent: Delegator, val tier: Int) : Delegate, ItemTier {
    override val unlocalizedName: String = super.unlocalizedName + tier

    // Because the driver considers the creative bus to be tier 3, the superclass
    // will believe it has T3 rarity. We override that here.
    override fun rarity(stack: ItemStack): EnumRarity =
        if (tier == Tier.Four) Rarity.byTier(Tier.Four)
        else super.rarity(stack)

    override val tooltipName: String? get() = super.unlocalizedName

    override val tooltipData: List<Any> get() = listOf(Settings.get.cpuComponentSupport(tier))
}
