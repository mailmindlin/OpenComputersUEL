package li.cil.oc.common.inventory

import li.cil.oc.Settings
import li.cil.oc.util.StackOption
import li.cil.oc.util.setNewTagList
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.Constants.NBT

interface Inventory : SimpleInventory {
    val items: Array<ItemStack>

    open fun updateItems(slot: Int, stack: ItemStack?) {
        items[slot] = StackOption(stack).orEmpty()
    }

    // ----------------------------------------------------------------------- //

    override fun getStackInSlot(slot: Int): ItemStack =
        if (slot in 0 until sizeInventory) items[slot]
        else ItemStack.EMPTY

    override fun setInventorySlotContents(slot: Int, stack: ItemStack) {
        if (slot !in 0 until sizeInventory)
            return

        if (stack.isEmpty && items[slot].isEmpty) {
            return
        }
        if (items[slot] === stack) {
            return
        }

        val oldStack = items[slot]
        updateItems(slot, ItemStack.EMPTY)
        if (!oldStack.isEmpty) {
            onItemRemoved(slot, oldStack)
        }
        if (!stack.isEmpty && stack.count >= inventoryStackRequired) {
            if (stack.count > inventoryStackLimit) {
                stack.count = inventoryStackLimit
            }
            updateItems(slot, stack)
        }

        if (!items[slot].isEmpty) {
            onItemAdded(slot, items[slot])
        }

        markDirty()
    }

    override fun getName(): String = Settings.namespace + "container." + inventoryName

    val inventoryName: String
        get() = javaClass.simpleName.lowercase()

    override fun isEmpty(): Boolean = items.all { it.isEmpty }

    // ----------------------------------------------------------------------- //

    companion object {
        private val ItemsTag = Settings.namespace + "items"
        private val SlotTag = "slot"
        private val ItemTag = "item"

        fun Inventory.load(nbt: NBTTagCompound) {
            val tagList = nbt.getTagList(ItemsTag, NBT.TAG_COMPOUND)
            for (i in 0 until tagList.tagCount()) {
                val tag = tagList.getCompoundTagAt(i)
                if (tag.hasKey(SlotTag)) {
                    val slot = tag.getByte(SlotTag).toInt()
                    if (slot >= 0 && slot < items.size) {
                        updateItems(slot, ItemStack(tag.getCompoundTag(ItemTag)))
                    }
                }
            }
        }

        fun Inventory.save(nbt: NBTTagCompound) {
            val itemsList = items.mapIndexedNotNull { slot, stack ->
                if (!stack.isEmpty) {
                    val slotNbt = NBTTagCompound()
                    slotNbt.setByte(SlotTag, slot.toByte())
                    val itemNbt = NBTTagCompound()
                    stack.writeToNBT(itemNbt)
                    slotNbt.setTag(ItemTag, itemNbt)
                    slotNbt
                } else {
                    null
                }
            }
            nbt.setNewTagList(ItemsTag, itemsList)
        }
    }


    // ----------------------------------------------------------------------- //

    fun onItemAdded(slot: Int, stack: ItemStack) {}
    fun onItemRemoved(slot: Int, stack: ItemStack) {}
}
