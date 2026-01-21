package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Robot
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.item.TabletWrapper
import li.cil.oc.server.component
import li.cil.oc.server.component.UpgradeTractorBeam
import net.minecraft.item.ItemStack

object DriverUpgradeTractorBeam : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.TractorBeamUpgrade))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world != null && host.world.isRemote) null
    else when (host) {
      is Drone -> UpgradeTractorBeam.Drone(host)
      is Robot -> component.UpgradeTractorBeam.Player(host, host.player())
      is TabletWrapper -> component.UpgradeTractorBeam.Player(host) { host.player }
      else -> null
    }

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun tier(stack: ItemStack) = Tier.Three

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        component.UpgradeTractorBeam.Common::class.java
      else null
  }
}
