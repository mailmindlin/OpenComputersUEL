package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.server.component.NetworkCard
import net.minecraft.item.ItemStack

object DriverNetworkCard : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.NetworkCard)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment? =
    if (host.world()?.isRemote != false) null
    else NetworkCard(host)

  override fun slot(stack: ItemStack) = Slot.Card

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        NetworkCard::class.java
      else null
  }
}
