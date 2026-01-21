package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.component
import li.cil.oc.common.tileentity
import net.minecraft.item.ItemStack

object DriverScreen : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.BlockName.ScreenTier1))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = when (host) {
    is tileentity.Screen -> if (host.tier > 0) component.Screen(host) else null
    else -> component.TextBuffer(host)
  }

  override fun slot(stack: ItemStack) = Slot.Upgrade

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        component.Screen::class.java
      else null
  }
}
