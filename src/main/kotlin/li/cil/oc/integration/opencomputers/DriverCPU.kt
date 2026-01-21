package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component
import li.cil.oc.server.machine.luac.NativeLuaArchitecture
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

object DriverCPU : DriverCPUClass()

abstract class DriverCPUClass : Item(), api.driver.item.MutableProcessor, api.driver.item.CallBudget {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    api.Items.get(Constants.ItemName.CPUTier1),
    api.Items.get(Constants.ItemName.CPUTier2),
    api.Items.get(Constants.ItemName.CPUTier3))

  override fun createEnvironment(stack: ItemStack, host: api.network.EnvironmentHost): api.network.ManagedEnvironment = component.CPU(tier(stack))

  override fun slot(stack: ItemStack) = Slot.CPU

  override fun tier(stack: ItemStack) = cpuTier(stack)

  open fun cpuTier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is item.CPU -> item.cpuTier
      else -> Tier.One
    }

  override fun supportedComponents(stack: ItemStack) = Settings.get.cpuComponentSupport(cpuTier(stack))

  override fun allArchitectures(): List<Class<out api.machine.Architecture>> = api.Machine.architectures().toList()

  override fun architecture(stack: ItemStack): Class<out api.machine.Architecture>? {
    if (stack.hasTagCompound) {
      val archClass = when (val clazz = stack.tagCompound.getString(Settings.namespace + "archClass")) {
        NativeLuaArchitecture::class.java.name -> {
          // Migrate old saved CPUs to new versions (since the class they refer still
          // exists, but is abstract, which would lead to issues).
          api.Machine.LuaArchitecture.name
        }
        else -> clazz
      }
      if (archClass.isNotEmpty()) {
        try {
          return Class.forName(archClass).asSubclass(api.machine.Architecture::class.java)
        } catch (t: Throwable) {
          OpenComputers.log.warn("Failed getting class for CPU architecture. Resetting CPU to use the default.", t)
          stack.tagCompound.removeTag(Settings.namespace + "archClass")
          stack.tagCompound.removeTag(Settings.namespace + "archName")
        }
      }
    }
    return api.Machine.architectures().firstOrNull()
  }

  override fun setArchitecture(stack: ItemStack, architecture: Class<out api.machine.Architecture>) {
    if (!worksWith(stack)) throw IllegalArgumentException("Unsupported processor type.")
    if (!stack.hasTagCompound) stack.tagCompound = NBTTagCompound()
    stack.tagCompound.setString(Settings.namespace + "archClass", architecture.name)
    stack.tagCompound.setString(Settings.namespace + "archName", api.Machine.getArchitectureName(architecture))
  }

  override fun getCallBudget(stack: ItemStack): Double = Settings.get.callBudgets(tier(stack).coerceIn(Tier.One, Tier.Three))
}
