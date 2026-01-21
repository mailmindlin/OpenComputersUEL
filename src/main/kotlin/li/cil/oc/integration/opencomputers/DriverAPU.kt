package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Tier
import li.cil.oc.common.item.APU as ItemAPU
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component.APU as ComponentAPU
import li.cil.oc.server.component.GraphicsCard
import net.minecraft.item.ItemStack

object DriverAPU : DriverCPU(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.ItemName.APUTier1),
    ApiItems.get(Constants.ItemName.APUTier2),
    ApiItems.get(Constants.ItemName.APUCreative))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else when (gpuTier(stack)) {
      Tier.One -> ComponentAPU(Tier.One)
      Tier.Two -> ComponentAPU(Tier.Two)
      Tier.Three -> ComponentAPU(Tier.Three)
      else -> null
    }

  override fun cpuTier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is ItemAPU -> item.cpuTier
      else -> Tier.One
    }

  fun gpuTier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is ItemAPU -> item.gpuTier
      else -> Tier.One
    }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        GraphicsCard::class.java
      else null
  }
}
