package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.GraphicsCard as ItemGraphicsCard
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component.GraphicsCard as ComponentGraphicsCard
import net.minecraft.item.ItemStack

object DriverGraphicsCard : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.ItemName.GraphicsCardTier1),
    ApiItems.get(Constants.ItemName.GraphicsCardTier2),
    ApiItems.get(Constants.ItemName.GraphicsCardTier3))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else when (tier(stack)) {
      Tier.One -> ComponentGraphicsCard(Tier.One)
      Tier.Two -> ComponentGraphicsCard(Tier.Two)
      Tier.Three -> ComponentGraphicsCard(Tier.Three)
      else -> null
    }

  override fun slot(stack: ItemStack) = Slot.Card

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is ItemGraphicsCard -> item.gpuTier
      else -> Tier.One
    }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        ComponentGraphicsCard::class.java
      else null
  }
}
