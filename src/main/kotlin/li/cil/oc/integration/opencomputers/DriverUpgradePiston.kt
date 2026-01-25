package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Drone as ApiDrone
import li.cil.oc.api.internal.Tablet as ApiTablet
import li.cil.oc.api.internal.Rotatable
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.server.component.UpgradePiston
import net.minecraft.item.ItemStack

object DriverUpgradePiston : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.ItemName.PistonUpgrade))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world() != null && host.world().isRemote) null
    else when (host) {
      is ApiDrone -> UpgradePiston.Drone(host)
      is ApiTablet -> UpgradePiston.Tablet(host)
      is Rotatable -> UpgradePiston.Rotatable(host)
      else -> null
    }

  override fun slot(stack: ItemStack) = Slot.Upgrade

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        UpgradePiston::class.java
      else null
  }
}
