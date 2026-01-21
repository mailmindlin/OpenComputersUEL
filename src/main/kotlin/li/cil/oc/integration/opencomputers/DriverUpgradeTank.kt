package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object DriverUpgradeTank : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.TankUpgrade))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else component.UpgradeTank(host, 16000)

  override fun slot(stack: ItemStack) = Slot.Upgrade
}
