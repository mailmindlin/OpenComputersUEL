package li.cil.oc.integration.opencomputers

import li.cil.oc.api.driver.InventoryProvider
import li.cil.oc.common.inventory.ServerInventory
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack

object InventoryProviderServer : InventoryProvider {
  override fun worksWith(stack: ItemStack, player: EntityPlayer): Boolean = DriverServer.worksWith(stack)

  override fun getInventory(stack: ItemStack, player: EntityPlayer): IInventory = object : ServerInventory() {
    override val container: ItemStack
      get() = stack
  }
}
