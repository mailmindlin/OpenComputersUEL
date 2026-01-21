package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common
import li.cil.oc.common.Tier
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object DriverAPU : DriverCPU(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.APUTier1),
    api.Items.get(Constants.ItemName.APUTier2),
    api.Items.get(Constants.ItemName.APUCreative))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else when (gpuTier(stack)) {
      Tier.One -> component.APU(Tier.One)
      Tier.Two -> component.APU(Tier.Two)
      Tier.Three -> component.APU(Tier.Three)
      else -> null
    }

  override fun cpuTier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is common.item.APU -> item.cpuTier
      else -> Tier.One
    }

  fun gpuTier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is common.item.APU -> item.gpuTier
      else -> Tier.One
    }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        component.GraphicsCard::class.java
      else null
  }
}
