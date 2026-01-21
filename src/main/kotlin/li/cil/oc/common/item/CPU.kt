package li.cil.oc.common.item

import li.cil.oc.common.item.traits.CPULike
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier

class CPU(override val parent: Delegator, val tier: Int) : Delegate, ItemTier, CPULike {
    override val unlocalizedName: String = super.unlocalizedName + tier

    override val cpuTier: Int get() = tier

    override val tooltipName: String? get() = super.unlocalizedName
}
