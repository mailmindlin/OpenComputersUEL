package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Adapter
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.tileentity.Robot
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object DriverUpgradeTankController : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.TankControllerUpgrade))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else when (host) {
      is Adapter -> component.UpgradeTankController.Adapter(host)
      is Drone -> component.UpgradeTankController.Drone(host)
      is Robot -> component.UpgradeTankController.Robot(host)
      else -> null
    }

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun tier(stack: ItemStack) = Tier.Two

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        component.UpgradeTankController.Robot::class.java
      else null
  }
}
