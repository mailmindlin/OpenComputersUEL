package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.server.component.EEPROM
import net.minecraft.item.ItemStack

object DriverEEPROM : Item() {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.ItemName.EEPROM))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else EEPROM()

  override fun slot(stack: ItemStack) = Slot.EEPROM

  override fun tier(stack: ItemStack) = Tier.One

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        EEPROM::class.java
      else null
  }
}
