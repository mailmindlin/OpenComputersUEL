package li.cil.oc.common.item

import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier

class DataCard(override val parent: Delegator, val tier: Int) : Delegate, ItemTier {
    override val unlocalizedName: String = super.unlocalizedName + tier
}
