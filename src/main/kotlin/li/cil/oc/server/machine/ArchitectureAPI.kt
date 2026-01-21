package li.cil.oc.server.machine

import li.cil.oc.api.machine.Machine as ApiMachine
import net.minecraft.nbt.NBTTagCompound

abstract class ArchitectureAPI(val machine: ApiMachine) {
  protected val node = machine.node()

  protected val components = machine.components()

  abstract fun initialize()
  open fun load(nbt: NBTTagCompound) {}
  open fun save(nbt: NBTTagCompound) {}
}
