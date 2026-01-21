package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.internal.Rack
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.server.component.Server
import li.cil.oc.util.ExtendedInventory.extendedInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

object DriverServer : Item(), HostAware {
  override fun worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    ApiItems.get(Constants.ItemName.ServerTier1),
    ApiItems.get(Constants.ItemName.ServerTier2),
    ApiItems.get(Constants.ItemName.ServerTier3),
    ApiItems.get(Constants.ItemName.ServerCreative))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment? = when (host) {
    is Rack -> Server(host, host.indexOf(stack))
    else -> null // Welp.
  }

  override fun slot(stack: ItemStack): String = Slot.RackMountable

  override fun dataTag(stack: ItemStack): NBTTagCompound {
    if (!stack.hasTagCompound) {
      stack.tagCompound = NBTTagCompound()
    }
    return stack.tagCompound
  }
}
