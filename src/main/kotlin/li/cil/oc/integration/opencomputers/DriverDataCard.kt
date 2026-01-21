package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.DataCard as ItemDataCard
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component.DataCard as ComponentDataCard
import net.minecraft.item.ItemStack

object DriverDataCard : Item() {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.ItemName.DataCardTier1),
    ApiItems.get(Constants.ItemName.DataCardTier2),
    ApiItems.get(Constants.ItemName.DataCardTier3))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else when (tier(stack)) {
      Tier.One -> ComponentDataCard.Tier1()
      Tier.Two -> ComponentDataCard.Tier2()
      Tier.Three -> ComponentDataCard.Tier3()
      else -> null
    }

  override fun slot(stack: ItemStack) = Slot.Card

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is ItemDataCard -> item.tier
      else -> Tier.One
    }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack)) when (tier(stack)) {
        Tier.One -> ComponentDataCard.Tier1::class.java
        Tier.Two -> ComponentDataCard.Tier2::class.java
        Tier.Three -> ComponentDataCard.Tier3::class.java
        else -> null
      }
      else null
  }
}
