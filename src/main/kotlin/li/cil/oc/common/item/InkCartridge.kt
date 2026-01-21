package li.cil.oc.common.item

import li.cil.oc.Constants
import li.cil.oc.api.Items
import net.minecraft.item.ItemStack

class InkCartridge(parent: Delegator) : AbstractDelegate(parent) {
    override val maxStackSize: Int = 1

    override fun getContainerItem(stack: ItemStack): ItemStack {
        return if (Items.get(stack) == Items.get(Constants.ItemName.InkCartridge))
            Items.get(Constants.ItemName.InkCartridgeEmpty).createItemStack(1)
        else
            super.getContainerItem(stack)
    }

    override fun hasContainerItem(stack: ItemStack): Boolean = true
}
