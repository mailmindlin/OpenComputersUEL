package li.cil.oc.common.inventory

import li.cil.oc.Localization
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.util.text.ITextComponent

interface SimpleInventory : IInventory {
    override fun hasCustomName(): Boolean = false

    override fun getDisplayName(): ITextComponent = Localization.localizeLater(name)

    override fun getInventoryStackLimit(): Int = 64

    // Items required in a slot before it's set to null (for ghost stacks).
    open fun getInventoryStackRequired(): Int = 1

    override fun openInventory(player: EntityPlayer) {}

    override fun closeInventory(player: EntityPlayer) {}

    override fun decrStackSize(slot: Int, amount: Int): ItemStack {
        if (slot >= 0 && slot < sizeInventory) {
            val stack = getStackInSlot(slot)
            val result = when {
                stack.isEmpty -> ItemStack.EMPTY
                stack.count - amount < getInventoryStackRequired() -> {
                    setInventorySlotContents(slot, ItemStack.EMPTY)
                    stack
                }
                else -> {
                    val splitResult = stack.splitStack(amount)
                    markDirty()
                    splitResult
                }
            }
            return if (!result.isEmpty && result.count > 0) result else ItemStack.EMPTY
        }
        return ItemStack.EMPTY
    }

    override fun removeStackFromSlot(slot: Int): ItemStack {
        return if (slot >= 0 && slot < sizeInventory) {
            val stack = getStackInSlot(slot)
            setInventorySlotContents(slot, ItemStack.EMPTY)
            stack
        } else {
            ItemStack.EMPTY
        }
    }

    override fun clear() {
        for (slot in 0 until sizeInventory) {
            setInventorySlotContents(slot, ItemStack.EMPTY)
        }
    }

    override fun getField(id: Int): Int = 0

    override fun setField(id: Int, value: Int) {}

    override fun getFieldCount(): Int = 0
}
