package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier

class UpgradeHover(override val parent: Delegator, val tier: Int) : Delegate, ItemTier {
    override val unlocalizedName: String = super.unlocalizedName + tier

    override val tooltipName: String? get() = super.unlocalizedName

    override val tooltipData: List<Any> get() = listOf(Settings.get.upgradeFlightHeight(tier))
}
