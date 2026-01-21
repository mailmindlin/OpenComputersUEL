package li.cil.oc.common.item

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.common.item.traits.Delegate
import net.minecraft.item.ItemStack

class InkCartridge(override val parent: Delegator) : Delegate {
    override val maxStackSize: Int = 1

    override fun getContainerItem(stack: ItemStack): ItemStack {
        return if (api.Items.get(stack) == api.Items.get(Constants.ItemName.InkCartridge))
            api.Items.get(Constants.ItemName.InkCartridgeEmpty).createItemStack(1)
        else
            super.getContainerItem(stack)
    }

    override fun hasContainerItem(stack: ItemStack): Boolean = true
}
