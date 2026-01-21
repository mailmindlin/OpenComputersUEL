package li.cil.oc.server.machine.luaj

import li.cil.oc.server.machine.ArchitectureAPI
import li.cil.repack.org.luaj.vm2.Globals

abstract class LuaJAPI(val owner: LuaJLuaArchitecture): ArchitectureAPI(owner.machine) {
  protected val lua get(): Globals = owner.lua!!
}
