package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.driver.item.Inventory
import li.cil.oc.common.Slot
import net.minecraft.item.ItemStack

object DriverUpgradeInventory : Item(), Inventory, HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.ItemName.InventoryUpgrade))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = null

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun inventoryCapacity(stack: ItemStack) = 16
}
