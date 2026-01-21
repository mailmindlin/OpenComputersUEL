package li.cil.oc.integration.opencomputers

import li.cil.oc.Settings
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.driver.DriverItem
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.internal.Adapter
import li.cil.oc.api.internal.Case
import li.cil.oc.api.internal.Drone as ApiDrone
import li.cil.oc.api.internal.Microcontroller
import li.cil.oc.api.internal.Robot
import li.cil.oc.api.internal.Rotatable
import li.cil.oc.api.internal.Server
import li.cil.oc.api.internal.Tablet
import li.cil.oc.common.Tier
import li.cil.oc.server.driver.Registry
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

abstract class Item : DriverItem {
  override fun worksWith(stack: ItemStack, host: Class<out EnvironmentHost>): Boolean =
    worksWith(stack) && !Registry.blacklist.any { (blacklistedStack, blacklistedHost) ->
      stack.isItemEqual(blacklistedStack) &&
        blacklistedHost.any { it.isAssignableFrom(host) }
    }

  override fun tier(stack: ItemStack) = Tier.One

  override fun dataTag(stack: ItemStack): NBTTagCompound = Item.dataTag(stack)

  protected fun isOneOf(stack: ItemStack, vararg items: ItemInfo): Boolean =
    items.filterNotNull().contains(ApiItems.get(stack))

  protected fun isAdapter(host: Class<out EnvironmentHost>): Boolean = Adapter::class.java.isAssignableFrom(host)

  protected fun isComputer(host: Class<out EnvironmentHost>): Boolean = Case::class.java.isAssignableFrom(host)

  protected fun isRobot(host: Class<out EnvironmentHost>): Boolean = Robot::class.java.isAssignableFrom(host)

  protected fun isRotatable(host: Class<out EnvironmentHost>): Boolean = Rotatable::class.java.isAssignableFrom(host)

  protected fun isServer(host: Class<out EnvironmentHost>): Boolean = Server::class.java.isAssignableFrom(host)

  protected fun isTablet(host: Class<out EnvironmentHost>): Boolean = Tablet::class.java.isAssignableFrom(host)

  protected fun isMicrocontroller(host: Class<out EnvironmentHost>): Boolean = Microcontroller::class.java.isAssignableFrom(host)

  protected fun isDrone(host: Class<out EnvironmentHost>): Boolean = ApiDrone::class.java.isAssignableFrom(host)

  companion object {
    @JvmStatic
    fun dataTag(stack: ItemStack): NBTTagCompound {
      if (!stack.hasTagCompound) {
        stack.tagCompound = NBTTagCompound()
      }
      val nbt = stack.tagCompound
      if (!nbt.hasKey(Settings.namespace + "data")) {
        nbt.setTag(Settings.namespace + "data", NBTTagCompound())
      }
      return nbt.getCompoundTag(Settings.namespace + "data")
    }

    private tailrec fun getTag(tagCompound: NBTTagCompound, keys: Array<String>): NBTTagCompound? {
      return when {
        keys.isEmpty() -> tagCompound
        !tagCompound.hasKey(keys[0]) -> null
        else -> getTag(tagCompound.getCompoundTag(keys[0]), keys.drop(1).toTypedArray())
      }
    }

    private fun getTag(stack: ItemStack?, keys: Array<String>): NBTTagCompound? {
      return when {
        stack == null || stack.count == 0 || stack.isEmpty -> null
        !stack.hasTagCompound -> null
        else -> getTag(stack.tagCompound, keys)
      }
    }

    @JvmStatic
    fun address(stack: ItemStack): String? {
      val addressKey = "address"
      return getTag(stack, arrayOf(Settings.namespace + "data", "node"))?.let { tag ->
        if (tag.hasKey(addressKey)) tag.getString(addressKey) else null
      }
    }
  }
}
