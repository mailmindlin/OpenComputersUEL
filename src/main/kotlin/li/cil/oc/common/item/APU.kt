package li.cil.oc.common.item

import li.cil.oc.common.Tier
import li.cil.oc.common.item.traits.CPULike
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.GPULike
import li.cil.oc.common.item.traits.ItemTier
import li.cil.oc.util.Rarity
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemStack
import kotlin.math.min

class APU(override val parent: Delegator, val tier: Int) : Delegate, ItemTier, CPULike, GPULike {
    override val unlocalizedName: String = super<Delegate>.unlocalizedName + tier

    override fun rarity(stack: ItemStack): EnumRarity =
        if (tier == Tier.Three) Rarity.byTier(Tier.Four)
        else super<Delegate>.rarity(stack)

    override val cpuTier: Int get() = min(Tier.Three, tier + 1)

    override val gpuTier: Int get() = tier

    override val tooltipName: String? get() = super<Delegate>.unlocalizedName

    override val tooltipData: List<Any>
        get() = super<CPULike>.tooltipData + super<GPULike>.tooltipData
}
