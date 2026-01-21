package li.cil.oc.common.item

import li.cil.oc.common.item.traits.Delegate

abstract class AbstractDelegate(override val parent: Delegator): Delegate {
    override var showInItemList: Boolean = true
    override val itemId: Int = 0
}