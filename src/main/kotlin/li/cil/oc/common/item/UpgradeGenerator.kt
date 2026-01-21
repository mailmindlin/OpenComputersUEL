package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier

class UpgradeGenerator(override val parent: Delegator) : Delegate, ItemTier {
    override val tooltipData: List<Any> get() = listOf((Settings.get.generatorEfficiency * 100).toInt())
}
