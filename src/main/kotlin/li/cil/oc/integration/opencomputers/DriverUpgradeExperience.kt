package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.server.component.UpgradeExperience
import net.minecraft.item.ItemStack

object DriverUpgradeExperience : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.ExperienceUpgrade)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    when (host) {
      is Agent -> UpgradeExperience(host)
      else -> null
    }

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun tier(stack: ItemStack) = Tier.Three

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        UpgradeExperience::class.java
      else null
  }
}
