package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.server.component.UpgradeGenerator
import net.minecraft.item.ItemStack

object DriverUpgradeGenerator : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.GeneratorUpgrade)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment? {
    if (host !is Agent) return null
    val world = host.world()
    if (world != null && world.isRemote) return null
    return UpgradeGenerator(host)
  }

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun tier(stack: ItemStack) = Tier.Two

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        UpgradeGenerator::class.java
      else null
  }
}
