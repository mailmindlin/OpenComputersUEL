package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.server.component
import li.cil.oc.server.component.UpgradeBarcodeReader
import net.minecraft.item.ItemStack

object DriverUpgradeBarcodeReader : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.Analyzer))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment =
    UpgradeBarcodeReader(host)

  override fun slot(stack: ItemStack) = li.cil.oc.common.Slot.Upgrade

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        component.UpgradeBarcodeReader::class.java
      else null
  }
}
