package li.cil.oc.common.item

import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.util.Rarity
import net.minecraft.item.ItemStack

class Microchip(override val parent: Delegator, val tier: Int) : Delegate {
    override val unlocalizedName: String = super.unlocalizedName + tier

    override val tooltipName: String? get() = super.unlocalizedName

    override fun rarity(stack: ItemStack) = Rarity.byTier(tier)
}
