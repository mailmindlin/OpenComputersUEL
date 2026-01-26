package li.cil.oc.integration.opencomputers

import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.server.component.UpgradeMF
import li.cil.oc.util.BlockPosition
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Items as ApiItems
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.DimensionManager

/**
 * @author Vexatos
 */
object DriverUpgradeMF : Item(), HostAware {
  override fun worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    Constants.ItemInfo.MFU)

  override fun worksWith(stack: ItemStack, host: Class<out EnvironmentHost>): Boolean =
    worksWith(stack) && isAdapter(host)

  override fun slot(stack: ItemStack): String = Slot.Upgrade

  override fun tier(stack: ItemStack) = Tier.Three

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment? {
    if (host.world() != null && !host.world().isRemote) {
      if (stack.hasTagCompound()) {
        val coord = stack.tagCompound!!.getIntArray(Settings.namespace + "coord")
        if (coord.size == 5) {
          val (x, y, z, dim, side) = coord
          val world = DimensionManager.getWorld(dim)
          if (world != null) {
            return UpgradeMF(host, BlockPosition(x, y, z, world), EnumFacing.byIndex(side))
          }
        }
      }
    }
    return null
  }

  object Provider : EnvironmentProvider {
    override fun getEnvironment(stack: ItemStack): Class<*>? =
      if (worksWith(stack))
        UpgradeMF::class.java
      else null
  }
}
