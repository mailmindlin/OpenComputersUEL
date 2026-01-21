package li.cil.oc.common.inventory

import li.cil.oc.Settings
import li.cil.oc.integration.opencomputers.DriverUpgradeDatabase
import net.minecraft.item.ItemStack

abstract class DatabaseInventory : ItemStackInventory() {
    open val tier: Int
        get() = DriverUpgradeDatabase.tier(container)

    override fun getSizeInventory(): Int = Settings.get.databaseEntriesPerTier(tier)

    override val inventoryName: String
        get() = "database"

    override fun getInventoryStackLimit(): Int = 1

    override fun getInventoryStackRequired(): Int = 1

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean = stack != container
}
