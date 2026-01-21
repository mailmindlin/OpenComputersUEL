package li.cil.oc.util

import net.minecraft.item.EnumRarity

object Rarity {
    private val lookup = arrayOf(EnumRarity.COMMON, EnumRarity.UNCOMMON, EnumRarity.RARE, EnumRarity.EPIC)

    @JvmStatic
    fun byTier(tier: Int): EnumRarity = lookup[tier.coerceIn(0, lookup.size - 1)]
}
