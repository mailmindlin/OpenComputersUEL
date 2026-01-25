package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Drone as ApiDrone
import li.cil.oc.api.internal.Tablet as ApiTablet
import li.cil.oc.api.internal.Rotatable
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.server.component.UpgradeStickyPiston
import net.minecraft.item.ItemStack

object DriverUpgradeStickyPiston : Item(), HostAware {
  override fun worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    ApiItems.get(Constants.ItemName.StickyPistonUpgrade))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment? =
    if (host.world() != null && host.world().isRemote) null
    else when (host) {
      is ApiDrone -> UpgradeStickyPiston.Drone(host)
      is ApiTablet -> UpgradeStickyPiston.Tablet(host)
      is Rotatable -> UpgradeStickyPiston.Rotatable(host)
      else -> null
    }

  override fun slot(stack: ItemStack): String = Slot.Upgrade

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        UpgradeStickyPiston::class.java
      else null
  }
}
