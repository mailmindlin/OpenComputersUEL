package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.server.component.Transposer
import net.minecraft.item.ItemStack

object DriverTransposer : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.BlockInfo.Transposer)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world() != null && host.world().isRemote) null
    else Transposer.Upgrade(host)

  override fun slot(stack: ItemStack) = Slot.Upgrade

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        Transposer.Upgrade::class.java
      else null
  }
}
