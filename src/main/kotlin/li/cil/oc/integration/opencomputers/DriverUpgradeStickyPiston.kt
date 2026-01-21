package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object DriverUpgradeStickyPiston : Item(), HostAware {
  override fun worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    api.Items.get(Constants.ItemName.StickyPistonUpgrade))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment? =
    if (host.world != null && host.world.isRemote) null
    else when (host) {
      is internal.Drone -> component.UpgradeStickyPiston.Drone(host)
      is internal.Tablet -> component.UpgradeStickyPiston.Tablet(host)
      is internal.Rotatable -> component.UpgradeStickyPiston.Rotatable(host)
      else -> null
    }

  override fun slot(stack: ItemStack): String = Slot.Upgrade

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        component.UpgradeStickyPiston::class.java
      else null
  }
}
