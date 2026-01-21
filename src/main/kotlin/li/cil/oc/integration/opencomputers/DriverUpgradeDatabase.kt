package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.inventory.DatabaseInventory
import li.cil.oc.common.item
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack

object DriverUpgradeDatabase : Item(), api.driver.item.HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.DatabaseUpgradeTier1),
    api.Items.get(Constants.ItemName.DatabaseUpgradeTier2),
    api.Items.get(Constants.ItemName.DatabaseUpgradeTier3))

  override fun createEnvironment(stack: ItemStack, host: api.network.EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else component.UpgradeDatabase(object : DatabaseInventory {
      override fun container() = stack

      override fun isUsableByPlayer(player: EntityPlayer) = false
    })

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is item.UpgradeDatabase -> item.tier
      else -> Tier.One
    }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        component.UpgradeDatabase::class.java
      else null
  }
}
