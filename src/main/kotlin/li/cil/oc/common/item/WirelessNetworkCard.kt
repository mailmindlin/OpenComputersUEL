package li.cil.oc.common.item

import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier

class WirelessNetworkCard(override val parent: Delegator, var tier: Int) : Delegate, ItemTier {
    override val unlocalizedName: String = super.unlocalizedName + tier

    override val tooltipName: String? get() = super.unlocalizedName
}
