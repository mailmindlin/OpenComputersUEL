package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Rack
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.common.component.TerminalServer
import li.cil.oc.util.asExtended
import net.minecraft.item.ItemStack

object DriverTerminalServer : Item(), HostAware {
  override fun worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    Constants.ItemInfo.TerminalServer)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment? = when (host) {
    is Rack -> TerminalServer(host, host.asExtended().indexOf(stack))
    else -> null
  }

  override fun slot(stack: ItemStack): String = Slot.RackMountable
}
