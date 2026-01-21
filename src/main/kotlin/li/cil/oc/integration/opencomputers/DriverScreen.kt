package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.component.Screen as ComponentScreen
import li.cil.oc.common.component.TextBuffer
import li.cil.oc.common.tileentity.Screen as TileEntityScreen
import net.minecraft.item.ItemStack

object DriverScreen : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.BlockName.ScreenTier1))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = when (host) {
    is TileEntityScreen -> if (host.tier > 0) ComponentScreen(host) else null
    else -> TextBuffer(host)
  }

  override fun slot(stack: ItemStack) = Slot.Upgrade

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        ComponentScreen::class.java
      else null
  }
}
