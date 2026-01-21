package li.cil.oc.common.item

import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier

class UpgradeTrading(override val parent: Delegator) : Delegate, ItemTier {
    override val tooltipName: String? get() = super.unlocalizedName
}
