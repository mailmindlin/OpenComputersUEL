package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.server.component.UpgradeAngel
import net.minecraft.item.ItemStack

object DriverUpgradeAngel : Item(), HostAware {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    Constants.ItemInfo.AngelUpgrade)

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost) =
    if (host.world() != null && host.world().isRemote) null
    else UpgradeAngel()

  override fun slot(stack: ItemStack) = Slot.Upgrade

  override fun tier(stack: ItemStack) = Tier.Two
}
