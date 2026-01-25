package li.cil.oc.common.inventory

import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

abstract class ItemStackInventory : Inventory {
    // The item stack that provides the inventory.
    abstract val container: ItemStack

    private val inventory: Array<ItemStack> by lazy {
        Array(sizeInventory) { ItemStack.EMPTY }
    }

    override val items: Array<ItemStack>
        get() = inventory

    // Initialize the list automatically if we have a container.
    init {
        @Suppress("LeakingThis")
        val _container = container
        if (!_container.isEmpty) {
            reinitialize()
        }
    }

    // Load items from tag.
    fun reinitialize() {
        for (i in items.indices) {
            updateItems(i, ItemStack.EMPTY)
        }
        if (!container.hasTagCompound()) {
            container.tagCompound = NBTTagCompound()
        }
        load(container.tagCompound!!)
    }

    // Write items back to tag.
    override fun markDirty() {
        if (!container.hasTagCompound()) {
            container.tagCompound = NBTTagCompound()
        }
        save(container.tagCompound!!)
    }
}
