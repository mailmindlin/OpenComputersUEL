package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.driver.item.Container
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.UpgradeContainerCard
import li.cil.oc.common.item.Delegator
import net.minecraft.item.ItemStack

object DriverContainerCard : Item(), Container {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.CardContainerTier1,
    Constants.ItemInfo.CardContainerTier2,
    Constants.ItemInfo.CardContainerTier3)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = null

  override fun slot(stack: ItemStack) = Slot.Container

  override fun providedSlot(stack: ItemStack) = Slot.Card

  override fun providedTier(stack: ItemStack) = tier(stack)

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is UpgradeContainerCard -> item.tier
      else -> Tier.One
    }
}
