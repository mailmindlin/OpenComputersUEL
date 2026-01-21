package li.cil.oc.common.item

import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier

class UpgradeChunkloader(override val parent: Delegator) : Delegate, ItemTier
