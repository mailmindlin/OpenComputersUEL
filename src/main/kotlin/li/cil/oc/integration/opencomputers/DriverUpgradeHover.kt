package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.UpgradeHover
import li.cil.oc.common.item.Delegator
import net.minecraft.item.ItemStack

object DriverUpgradeHover : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.HoverUpgradeTier1,
    Constants.ItemInfo.HoverUpgradeTier2)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = null

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is UpgradeHover -> item.tier
      else -> Tier.One
    }
}
