package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.common.component.TerminalServer
import li.cil.oc.util.ExtendedInventory.extendedInventory
import net.minecraft.item.ItemStack

object DriverTerminalServer : Item(), HostAware {
  override fun worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    api.Items.get(Constants.ItemName.TerminalServer))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment? = when (host) {
    is api.internal.Rack -> TerminalServer(host, host.indexOf(stack))
    else -> null
  }

  override fun slot(stack: ItemStack): String = Slot.RackMountable
}
