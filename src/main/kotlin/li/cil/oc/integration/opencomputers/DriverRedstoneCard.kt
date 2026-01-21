package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.tileentity.traits.BundledRedstoneAware
import li.cil.oc.common.tileentity.traits.RedstoneAware
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.integration.util.WirelessRedstone
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object DriverRedstoneCard : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.RedstoneCardTier1),
    api.Items.get(Constants.ItemName.RedstoneCardTier2))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else {
      val isAdvanced = tier(stack) == Tier.Two
      val hasBundled = BundledRedstone.isAvailable && isAdvanced
      val hasWireless = WirelessRedstone.isAvailable && isAdvanced
      when (host) {
        is BundledRedstoneAware -> if (hasBundled) {
          if (hasWireless) component.Redstone.BundledWireless(host)
          else component.Redstone.Bundled(host)
        } else null
        is RedstoneAware -> {
          if (hasWireless) component.Redstone.VanillaWireless(host)
          else component.Redstone.Vanilla(host)
        }
        else -> {
          if (hasWireless) component.Redstone.Wireless(host)
          else null
        }
      }
    }

  override fun slot(stack: ItemStack) = Slot.Card

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is item.RedstoneCard -> item.tier
      else -> Tier.One
    }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack)) {
        val isAdvanced = tier(stack) == Tier.Two
        val hasBundled = BundledRedstone.isAvailable && isAdvanced
        val hasWireless = WirelessRedstone.isAvailable && isAdvanced
        if (hasBundled) {
          if (hasWireless) component.Redstone.BundledWireless::class.java
          else component.Redstone.Bundled::class.java
        }
        else {
          component.Redstone.Vanilla::class.java
        }
      }
      else null
  }
}
