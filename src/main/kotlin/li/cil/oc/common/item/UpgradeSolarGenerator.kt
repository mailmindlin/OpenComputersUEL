package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier

class UpgradeSolarGenerator(override val parent: Delegator) : Delegate, ItemTier {
    override val tooltipData: List<Any> get() = listOf((Settings.get.solarGeneratorEfficiency * 100).toInt())
}
