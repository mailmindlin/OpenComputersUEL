package li.cil.oc.common.item

import li.cil.oc.common.item.traits.Delegate

class InkCartridgeEmpty(override val parent: Delegator) : Delegate {
    override val maxStackSize: Int = 1
}
