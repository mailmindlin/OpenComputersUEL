package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.RedstoneCard as ItemRedstoneCard
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.tileentity.traits.BundledRedstoneAware
import li.cil.oc.common.tileentity.traits.RedstoneAware
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.integration.util.WirelessRedstone
import li.cil.oc.server.component.Redstone as ComponentRedstone
import net.minecraft.item.ItemStack

object DriverRedstoneCard : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.RedstoneCardTier1,
    Constants.ItemInfo.RedstoneCardTier2)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment? {
    if (host.world()?.isRemote == true) return null
    val isAdvanced = tier(stack) == Tier.Two
    val hasBundled = BundledRedstone.isAvailable && isAdvanced
    val hasWireless = WirelessRedstone.isAvailable() && isAdvanced
    when (host) {
      is BundledRedstoneAware -> {
        if (!hasBundled)
          return null
        return if (hasWireless) ComponentRedstone.BundledWireless(host)
        else ComponentRedstone.Bundled(host)
      }
      is RedstoneAware -> {
        return if (hasWireless) ComponentRedstone.VanillaWireless(host)
        else ComponentRedstone.Vanilla(host)
      }
      else -> {
        if (!hasWireless) return null
        return ComponentRedstone.Wireless(host)
      }
    }
  }

  override fun slot(stack: ItemStack) = Slot.Card

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is ItemRedstoneCard -> item.tier
      else -> Tier.One
    }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack)) {
        val isAdvanced = tier(stack) == Tier.Two
        val hasBundled = BundledRedstone.isAvailable && isAdvanced
        val hasWireless = WirelessRedstone.isAvailable() && isAdvanced
        if (hasBundled) {
          if (hasWireless) ComponentRedstone.BundledWireless::class.java
          else ComponentRedstone.Bundled::class.java
        }
        else {
          ComponentRedstone.Vanilla::class.java
        }
      }
      else null
  }
}
