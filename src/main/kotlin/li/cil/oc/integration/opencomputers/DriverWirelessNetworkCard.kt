package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.Delegator
import net.minecraft.item.ItemStack
import li.cil.oc.common.item.WirelessNetworkCard as ItemWirelessNetworkCard
import li.cil.oc.server.component.WirelessNetworkCard as ComponentWirelessNetworkCard

object DriverWirelessNetworkCard : Item() {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.WirelessNetworkCardTier1,
    Constants.ItemInfo.WirelessNetworkCardTier2)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world() != null && host.world().isRemote) null
    else when (tier(stack)) {
      Tier.One -> ComponentWirelessNetworkCard.Tier1(host)
      Tier.Two -> ComponentWirelessNetworkCard.Tier2(host)
      else -> null
    }

  override fun slot(stack: ItemStack) = Slot.Card

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is ItemWirelessNetworkCard -> item.tier
      else -> Tier.One
    }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack)) when (tier(stack)) {
        Tier.One -> ComponentWirelessNetworkCard.Tier1::class.java
        Tier.Two -> ComponentWirelessNetworkCard.Tier2::class.java
        else -> null
      }
      else null
  }
}
