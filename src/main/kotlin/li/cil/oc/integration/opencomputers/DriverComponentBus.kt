package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.driver.item.Processor
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item
import li.cil.oc.common.item.Delegator
import net.minecraft.item.ItemStack

object DriverComponentBus : Item(), Processor {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.ComponentBusTier1),
    api.Items.get(Constants.ItemName.ComponentBusTier2),
    api.Items.get(Constants.ItemName.ComponentBusTier3),
    api.Items.get(Constants.ItemName.ComponentBusCreative))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = null

  override fun slot(stack: ItemStack) = Slot.ComponentBus

  // Clamp item tier because the creative bus needs to fit into tier 3 slots.
  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is item.ComponentBus -> item.tier.coerceAtMost(Tier.Three)
      else -> Tier.One
    }

  override fun supportedComponents(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is item.ComponentBus -> Settings.get.cpuComponentSupport(item.tier)
      else -> Tier.One
    }

  override fun architecture(stack: ItemStack) = null
}
