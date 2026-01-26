package li.cil.oc.common.inventory

import li.cil.oc.util.ensureTagCompound
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
        if (!container.isEmpty)
            reinitialize()
    }

    // Load items from tag.
    fun reinitialize() {
        for (i in items.indices)
            updateItems(i, ItemStack.EMPTY)
        load(container.ensureTagCompound)
    }

    // Write items back to tag.
    override fun markDirty() {
        save(container.ensureTagCompound)
    }
}
