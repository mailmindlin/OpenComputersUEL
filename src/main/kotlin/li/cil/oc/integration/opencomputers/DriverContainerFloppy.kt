package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.driver.item.Container
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import net.minecraft.item.ItemStack

object DriverContainerFloppy : Item(), Container {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.BlockName.DiskDrive))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) = null

  override fun slot(stack: ItemStack) = Slot.Container

  override fun providedSlot(stack: ItemStack) = Slot.Floppy

  override fun providedTier(stack: ItemStack) = Tier.Any
}
