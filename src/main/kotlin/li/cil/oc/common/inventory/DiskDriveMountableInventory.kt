package li.cil.oc.common.inventory

import li.cil.oc.api.Driver
import li.cil.oc.common.tileentity
import li.cil.oc.common.Slot
import net.minecraft.item.ItemStack

abstract class DiskDriveMountableInventory : ItemStackInventory() {
    open val tier: Int
        get() = 1

    override fun getSizeInventory(): Int = 1

    override val inventoryName: String
        get() = "diskdrive"

    override fun getInventoryStackLimit(): Int = 1

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        if (slot != 0) return false
        val driver = Driver.driverFor(stack, tileentity.DiskDrive::class.java)
        return driver != null && driver.slot(stack) == Slot.Floppy
    }
}
