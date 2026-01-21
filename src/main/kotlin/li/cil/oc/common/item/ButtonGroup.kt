package li.cil.oc.common.item

import li.cil.oc.common.item.traits.Delegate

class ButtonGroup(override val parent: Delegator) : Delegate {
    override val tooltipName: String? = null
}
