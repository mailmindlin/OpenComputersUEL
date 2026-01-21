package li.cil.oc.util

import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack

class ExtendedInventory(val inventory: IInventory) : AbstractMutableList<ItemStack>() {
    override val size: Int get() = inventory.sizeInventory

    override fun set(index: Int, element: ItemStack): ItemStack {
        val old = inventory.getStackInSlot(index)
        inventory.setInventorySlotContents(index, element)
        return old
    }

    override fun get(index: Int): ItemStack = inventory.getStackInSlot(index)

    override fun add(index: Int, element: ItemStack) {
        throw UnsupportedOperationException("Cannot add to inventory")
    }

    override fun removeAt(index: Int): ItemStack {
        throw UnsupportedOperationException("Cannot remove from inventory")
    }
}

fun IInventory.asExtended(): ExtendedInventory = ExtendedInventory(this)
