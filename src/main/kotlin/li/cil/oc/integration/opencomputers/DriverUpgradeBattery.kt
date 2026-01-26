package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.UpgradeBattery as ItemUpgradeBattery
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component.UpgradeBattery as ComponentUpgradeBattery
import net.minecraft.item.ItemStack

object DriverUpgradeBattery : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.BatteryUpgradeTier1,
    Constants.ItemInfo.BatteryUpgradeTier2,
    Constants.ItemInfo.BatteryUpgradeTier3)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world() != null && host.world().isRemote) null
    else ComponentUpgradeBattery(tier(stack))

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is ItemUpgradeBattery -> item.tier
      else -> Tier.One
    }
}
