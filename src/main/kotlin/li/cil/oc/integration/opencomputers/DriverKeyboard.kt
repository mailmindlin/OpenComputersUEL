package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.common.Slot
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object DriverKeyboard : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.BlockName.Keyboard))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = component.Keyboard(host)

  override fun slot(stack: ItemStack) = Slot.Upgrade
}
