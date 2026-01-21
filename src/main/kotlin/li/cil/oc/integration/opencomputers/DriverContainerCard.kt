package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.driver.item.Container
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item
import li.cil.oc.common.item.Delegator
import net.minecraft.item.ItemStack

object DriverContainerCard : Item(), Container {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.CardContainerTier1),
    api.Items.get(Constants.ItemName.CardContainerTier2),
    api.Items.get(Constants.ItemName.CardContainerTier3))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = null

  override fun slot(stack: ItemStack) = Slot.Container

  override fun providedSlot(stack: ItemStack) = Slot.Card

  override fun providedTier(stack: ItemStack) = tier(stack)

  override fun tier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is item.UpgradeContainerCard -> item.tier
      else -> Tier.One
    }
}
