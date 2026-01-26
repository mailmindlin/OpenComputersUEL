package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Robot
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.server.component.UpgradeCrafting
import net.minecraft.item.ItemStack

object DriverUpgradeCrafting : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.CraftingUpgrade)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world() != null && host.world().isRemote) null
    else when (host) {
      is Robot -> UpgradeCrafting(host)
      else -> null
    }

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun tier(stack: ItemStack) = Tier.Two

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        UpgradeCrafting::class.java
      else null
  }
}
