package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.inventory.DatabaseInventory
import li.cil.oc.common.item.UpgradeDatabase as ItemUpgradeDatabase
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component.UpgradeDatabase as ComponentUpgradeDatabase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack

object DriverUpgradeDatabase : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.DatabaseUpgradeTier1,
    Constants.ItemInfo.DatabaseUpgradeTier2,
    Constants.ItemInfo.DatabaseUpgradeTier3)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world() != null && host.world().isRemote) null
    else ComponentUpgradeDatabase(object : DatabaseInventory() {
      override val container: ItemStack
        get() = stack

      override fun isUsableByPlayer(player: EntityPlayer) = false
    })

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is ItemUpgradeDatabase -> item.tier
      else -> Tier.One
    }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        ComponentUpgradeDatabase::class.java
      else null
  }
}
