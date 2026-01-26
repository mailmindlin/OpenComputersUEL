package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.common.Slot
import li.cil.oc.server.component.Keyboard
import net.minecraft.item.ItemStack

object DriverKeyboard : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.BlockInfo.Keyboard)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = Keyboard(host)

  override fun slot(stack: ItemStack) = Slot.Upgrade
}
