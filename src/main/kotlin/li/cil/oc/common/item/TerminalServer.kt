package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate

class TerminalServer(override val parent: Delegator) : Delegate {
    override val tooltipData: List<Any> get() = listOf(Settings.get.terminalsPerServer)
}
