package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Adapter
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.tileentity.Robot
import li.cil.oc.server.component.UpgradeInventoryController
import net.minecraft.item.ItemStack

object DriverUpgradeInventoryController : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.ItemName.InventoryControllerUpgrade))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment? =
    if (host.world() != null && host.world().isRemote) null
    else when (host) {
      is Adapter -> UpgradeInventoryController.Adapter(host)
      is Drone -> UpgradeInventoryController.Drone(host)
      is Robot -> UpgradeInventoryController.Robot(host)
      else -> null
    }

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun tier(stack: ItemStack) = Tier.Two

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        UpgradeInventoryController.Robot::class.java
      else null
  }
}
