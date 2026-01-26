package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.item.CallBudget
import li.cil.oc.api.driver.item.Memory as MemoryDriver
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.Memory as ItemMemory
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component.Memory as ComponentMemory
import net.minecraft.item.ItemStack

object DriverMemory : Item(), MemoryDriver, CallBudget {
  override fun amount(stack: ItemStack): Double = when (val item = Delegator.subItem(stack)) {
    is ItemMemory -> {
      val sizes = Settings.get.ramSizes
      Settings.get.ramSizes[item.tier.coerceIn(0, sizes.size - 1)].toDouble()
    }
    else -> 0.0
  }

  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.RAMTier1,
    Constants.ItemInfo.RAMTier2,
    Constants.ItemInfo.RAMTier3,
    Constants.ItemInfo.RAMTier4,
    Constants.ItemInfo.RAMTier5,
    Constants.ItemInfo.RAMTier6)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = ComponentMemory(tier(stack))

  override fun slot(stack: ItemStack) = Slot.Memory

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is ItemMemory -> item.tier / 2
      else -> Tier.One
    }

  override fun getCallBudget(stack: ItemStack): Double = Settings.get.callBudgets[tier(stack).coerceIn(Tier.One, Tier.Three)]
}
