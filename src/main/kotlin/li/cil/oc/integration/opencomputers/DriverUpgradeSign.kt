package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Adapter
import li.cil.oc.api.internal.Rotatable
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.server.component.UpgradeSign
import net.minecraft.item.ItemStack

object DriverUpgradeSign : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.SignUpgrade)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world() != null && host.world().isRemote) null
    else when (host) {
      is Rotatable -> UpgradeSign.UpgradeSignInRotatable(host)
      is Adapter -> UpgradeSign.UpgradeSignInAdapter(host)
      else -> null
    }

  override fun slot(stack: ItemStack) = Slot.Upgrade

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        UpgradeSign::class.java
      else null
  }
}
