package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object DriverMemory : Item(), api.driver.item.Memory, api.driver.item.CallBudget {
  override fun amount(stack: ItemStack): Double = when (val item = Delegator.subItem(stack)) {
    is item.Memory -> {
      val sizes = Settings.get.ramSizes
      Settings.get.ramSizes[item.tier.coerceIn(0, sizes.size - 1)]
    }
    else -> 0.0
  }

  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.RAMTier1),
    api.Items.get(Constants.ItemName.RAMTier2),
    api.Items.get(Constants.ItemName.RAMTier3),
    api.Items.get(Constants.ItemName.RAMTier4),
    api.Items.get(Constants.ItemName.RAMTier5),
    api.Items.get(Constants.ItemName.RAMTier6))

  override fun createEnvironment(stack: ItemStack, host: api.network.EnvironmentHost) = component.Memory(tier(stack))

  override fun slot(stack: ItemStack) = Slot.Memory

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is item.Memory -> item.tier / 2
      else -> Tier.One
    }

  override fun getCallBudget(stack: ItemStack): Double = Settings.get.callBudgets(tier(stack).coerceIn(Tier.One, Tier.Three))
}
