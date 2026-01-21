package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.driver.item.HostAware
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.server.component
import li.cil.oc.util.ExtendedInventory.extendedInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

object DriverServer : Item(), HostAware {
  override fun worksWith(stack: ItemStack): Boolean = isOneOf(stack,
    api.Items.get(Constants.ItemName.ServerTier1),
    api.Items.get(Constants.ItemName.ServerTier2),
    api.Items.get(Constants.ItemName.ServerTier3),
    api.Items.get(Constants.ItemName.ServerCreative))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment? = when (host) {
    is api.internal.Rack -> component.Server(host, host.indexOf(stack))
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
