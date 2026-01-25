package li.cil.oc.common.item

import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier

class DataCard(parent: Delegator, internal val tier: Int) : AbstractDelegate(parent), ItemTier {
    override val unlocalizedName: String = super<AbstractDelegate>.unlocalizedName + tier
}
