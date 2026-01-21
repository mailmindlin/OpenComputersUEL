package li.cil.oc.common.item

import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.GPULike
import li.cil.oc.common.item.traits.ItemTier

class GraphicsCard(override val parent: Delegator, val tier: Int) : Delegate, ItemTier, GPULike {
    override val unlocalizedName: String = super.unlocalizedName + tier

    override val gpuTier: Int get() = tier

    override val tooltipName: String? get() = super.unlocalizedName
}
