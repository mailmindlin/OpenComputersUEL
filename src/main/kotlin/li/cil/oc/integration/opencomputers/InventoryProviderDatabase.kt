package li.cil.oc.integration.opencomputers

import li.cil.oc.api.driver.InventoryProvider
import li.cil.oc.common.inventory.DatabaseInventory
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack

object InventoryProviderDatabase : InventoryProvider {
  override fun worksWith(stack: ItemStack, player: EntityPlayer): Boolean = DriverUpgradeDatabase.worksWith(stack)

  override fun getInventory(stack: ItemStack, player: EntityPlayer): IInventory = object : DatabaseInventory {
    override fun container(): ItemStack = stack

    override fun isUsableByPlayer(player: EntityPlayer): Boolean = true
  }
}
