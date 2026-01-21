package li.cil.oc.common.item

import li.cil.oc.common.Tier
import li.cil.oc.common.item.traits.CPULike
import li.cil.oc.common.item.traits.GPULike
import li.cil.oc.common.item.traits.ItemTier
import li.cil.oc.util.Rarity
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemStack
import kotlin.math.min

class APU(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier, CPULike, GPULike {
    override fun rarity(stack: ItemStack): EnumRarity =
        if (tier == Tier.Three) Rarity.byTier(Tier.Four)
        else super<AbstractTieredDelegate>.rarity(stack)

    override val cpuTier: Int get() = min(Tier.Three, tier + 1)

    override val gpuTier: Int get() = tier

    override val tooltipData: Array<Any>
        get() = arrayOf(*super<CPULike>.tooltipData, *super<GPULike>.tooltipData)
}
