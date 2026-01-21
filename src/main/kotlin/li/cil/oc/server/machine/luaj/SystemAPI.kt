package li.cil.oc.server.machine.luaj

import li.cil.oc.Settings
import li.cil.repack.org.luaj.vm2.LuaValue

internal class SystemAPI(owner: LuaJLuaArchitecture): LuaJAPI(owner) {
  override fun initialize() {
    val system = LuaValue.tableOf()

    // Whether bytecode may be loaded directly.
    system.setClosure("allowBytecode") { LuaValue.valueOf(Settings.get.allowBytecode) }

    // Whether custom __gc callbacks are allowed.
    system.setClosure("allowGC") { LuaValue.valueOf(Settings.get.allowGC) }

    // How long programs may run without yielding before we stop them.
    system.setClosure("timeout") { LuaValue.valueOf(Settings.get.timeout) }

    lua.set("system", system)
  }
}
