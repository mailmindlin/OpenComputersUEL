package li.cil.oc.common.item

import li.cil.oc.common.item.traits.CPULike
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier

class CPU(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier, CPULike {
    override val cpuTier: Int get() = tier
}
