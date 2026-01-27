package li.cil.oc.common.item.data

import li.cil.oc.OpenComputers
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.Persistable
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

abstract class ItemData(private val itemName: String?) : Persistable {
    open fun load(stack: ItemStack) {
        if (stack.hasTagCompound()) {
            // Because ItemStack's load function doesn't copy the compound tag,
            // but keeps it as is, leading to oh so fun bugs!
            load(stack.tagCompound!!.copy() as NBTTagCompound)
        }
    }

    open fun save(stack: ItemStack) {
        if (!stack.hasTagCompound()) {
            stack.tagCompound = NBTTagCompound()
        }
        save(stack.tagCompound!!)
    }

    fun createItemStack(): ItemStack {
        if (itemName == null) OpenComputers.log.warn("Unknown itemData $this")
        if (itemName == null) return ItemStack.EMPTY
        val itemInfo = ApiItems.get(itemName) ?: throw NullPointerException("missing itemInfo for $itemName")
        val stack = itemInfo.createItemStack(1)
        save(stack)
        return stack
    }
}
