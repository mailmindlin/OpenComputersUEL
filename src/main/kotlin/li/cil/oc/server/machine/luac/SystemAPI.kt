package li.cil.oc.server.machine.luac

import li.cil.oc.Settings
import li.cil.repack.com.naef.jnlua.LuaType

class SystemAPI(owner: NativeLuaArchitecture): NativeLuaAPI(owner) {
  override fun initialize() {
    // Until we get to ingame screens we log to Java's stdout.
    lua.pushJavaFunction { lua ->
      println(
        (1 .. lua.top)
        .map { i ->
          when (lua.type(i)) {
            LuaType.NIL, null -> "nil"
            LuaType.BOOLEAN -> lua.toBoolean(i)
            LuaType.NUMBER -> if (lua.isInteger(i)) lua.toInteger(i) else lua.toNumber(i)
            LuaType.STRING -> lua.toString(i)
            LuaType.TABLE -> "table"
            LuaType.FUNCTION -> "function"
            LuaType.THREAD -> "thread"
            LuaType.LIGHTUSERDATA, LuaType.USERDATA -> "userdata"
          }
        }.joinToString("  "))
      0
    }
    lua.setGlobal("print")

    // Create system table, avoid magic global non-tables.
    lua.newTable()

    // Whether bytecode may be loaded directly.
    lua.pushJavaFunction { lua ->
      lua.pushBoolean(Settings.get.allowBytecode)
      1
    }
    lua.setField(-2, "allowBytecode")

    // Whether custom __gc callbacks are allowed.
    lua.pushJavaFunction { lua ->
      lua.pushBoolean(Settings.get.allowGC)
      1
    }
    lua.setField(-2, "allowGC")

    // How long programs may run without yielding before we stop them.
    lua.pushJavaFunction { lua ->
      lua.pushNumber(Settings.get.timeout)
      1
    }
    lua.setField(-2, "timeout")

    lua.setGlobal("system");
  }
}
