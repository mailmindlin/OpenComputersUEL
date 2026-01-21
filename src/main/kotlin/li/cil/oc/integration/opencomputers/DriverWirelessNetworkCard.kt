package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object DriverWirelessNetworkCard : Item() {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.WirelessNetworkCardTier1),
    api.Items.get(Constants.ItemName.WirelessNetworkCardTier2))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else when (tier(stack)) {
      Tier.One -> component.WirelessNetworkCard.Tier1(host)
      Tier.Two -> component.WirelessNetworkCard.Tier2(host)
      else -> null
    }

  override fun slot(stack: ItemStack) = Slot.Card

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is common.item.WirelessNetworkCard -> item.tier
      else -> Tier.One
    }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack)) when (tier(stack)) {
        Tier.One -> component.WirelessNetworkCard.Tier1::class.java
        Tier.Two -> component.WirelessNetworkCard.Tier2::class.java
        else -> null
      }
      else null
  }
}
