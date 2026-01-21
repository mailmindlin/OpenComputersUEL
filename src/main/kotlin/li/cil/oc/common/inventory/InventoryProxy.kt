package li.cil.oc.common.inventory

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.util.text.ITextComponent

interface InventoryProxy : IInventory {
    val inventory: IInventory

    val offset: Int
        get() = 0

    override fun isEmpty(): Boolean = inventory.isEmpty

    override fun getSizeInventory(): Int = inventory.sizeInventory

    override fun getInventoryStackLimit(): Int = inventory.inventoryStackLimit

    override fun getName(): String = inventory.name

    override fun getDisplayName(): ITextComponent = inventory.displayName

    override fun hasCustomName(): Boolean = inventory.hasCustomName()

    override fun isUsableByPlayer(player: EntityPlayer): Boolean = inventory.isUsableByPlayer(player)

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        val offsetSlot = slot + offset
        return isValidSlot(offsetSlot) && inventory.isItemValidForSlot(offsetSlot, stack)
    }

    override fun getStackInSlot(slot: Int): ItemStack {
        val offsetSlot = slot + offset
        return if (isValidSlot(offsetSlot)) inventory.getStackInSlot(offsetSlot)
        else ItemStack.EMPTY
    }

    override fun decrStackSize(slot: Int, amount: Int): ItemStack {
        val offsetSlot = slot + offset
        return if (isValidSlot(offsetSlot)) inventory.decrStackSize(offsetSlot, amount)
        else ItemStack.EMPTY
    }

    override fun removeStackFromSlot(slot: Int): ItemStack {
        val offsetSlot = slot + offset
        return if (isValidSlot(offsetSlot)) inventory.removeStackFromSlot(offsetSlot)
        else ItemStack.EMPTY
    }

    override fun setInventorySlotContents(slot: Int, stack: ItemStack) {
        val offsetSlot = slot + offset
        if (isValidSlot(offsetSlot)) inventory.setInventorySlotContents(offsetSlot, stack)
    }

    override fun markDirty() = inventory.markDirty()

    override fun openInventory(player: EntityPlayer) = inventory.openInventory(player)

    override fun closeInventory(player: EntityPlayer) = inventory.closeInventory(player)

    override fun setField(id: Int, value: Int) = inventory.setField(id, value)

    override fun clear() = inventory.clear()

    override fun getFieldCount(): Int = inventory.fieldCount

    override fun getField(id: Int): Int = inventory.getField(id)

    private fun isValidSlot(slot: Int): Boolean = slot >= offset && slot < sizeInventory + offset
}
