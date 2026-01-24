package li.cil.oc.integration.opencomputers

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.Machine
import li.cil.oc.api.driver.item.CallBudget
import li.cil.oc.api.driver.item.MutableProcessor
import li.cil.oc.api.machine.Architecture
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.CPU as ItemCPU
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.component.CPU as ComponentCPU
import li.cil.oc.server.machine.luac.NativeLuaArchitecture
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound

object DriverCPU : DriverCPUClass()

abstract class DriverCPUClass : Item(), MutableProcessor, CallBudget {
  override fun worksWith(stack: ItemStack) = isOneOf(stack,
    ApiItems.get(Constants.ItemName.CPUTier1),
    ApiItems.get(Constants.ItemName.CPUTier2),
    ApiItems.get(Constants.ItemName.CPUTier3))

  override fun createEnvironment(stack: ItemStack, host: EnvironmentHost): ManagedEnvironment = ComponentCPU(tier(stack))

  override fun slot(stack: ItemStack) = Slot.CPU

  override fun tier(stack: ItemStack) = cpuTier(stack)

  open fun cpuTier(stack: ItemStack): Int =
    when (val item = Delegator.subItem(stack)) {
      is ItemCPU -> item.cpuTier
      else -> Tier.One
    }

  override fun supportedComponents(stack: ItemStack) = Settings.get.cpuComponentSupport(cpuTier(stack))

  override fun allArchitectures(): List<Class<out Architecture>> = Machine.architectures().toList()

  override fun architecture(stack: ItemStack): Class<out Architecture>? {
    if (stack.hasTagCompound()) {
      val archClass = when (val clazz = stack.tagCompound.getString(Settings.namespace + "archClass")) {
        NativeLuaArchitecture::class.java.name -> {
          // Migrate old saved CPUs to new versions (since the class they refer still
          // exists, but is abstract, which would lead to issues).
          Machine.LuaArchitecture.name
        }
        else -> clazz
      }
      if (archClass.isNotEmpty()) {
        try {
          return Class.forName(archClass).asSubclass(Architecture::class.java)
        } catch (t: Throwable) {
          OpenComputers.log.warn("Failed getting class for CPU architecture. Resetting CPU to use the default.", t)
          stack.tagCompound.removeTag(Settings.namespace + "archClass")
          stack.tagCompound.removeTag(Settings.namespace + "archName")
        }
      }
    }
    return Machine.architectures().firstOrNull()
  }

  override fun setArchitecture(stack: ItemStack, architecture: Class<out Architecture>) {
    if (!worksWith(stack)) throw IllegalArgumentException("Unsupported processor type.")
    if (!stack.hasTagCompound()) stack.tagCompound = NBTTagCompound()
    stack.tagCompound.setString(Settings.namespace + "archClass", architecture.name)
    stack.tagCompound.setString(Settings.namespace + "archName", Machine.getArchitectureName(architecture))
  }

  override fun getCallBudget(stack: ItemStack): Double = Settings.get.callBudgets(tier(stack).coerceIn(Tier.One, Tier.Three))
}
