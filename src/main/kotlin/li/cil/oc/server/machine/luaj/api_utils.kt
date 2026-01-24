package li.cil.oc.server.machine.luaj

import li.cil.repack.org.luaj.vm2.LuaTable
import li.cil.repack.org.luaj.vm2.LuaValue
import li.cil.repack.org.luaj.vm2.Varargs


internal fun LuaTable.setClosure(key: String, value: (Varargs) -> Varargs) {
    this.set(key, LuaClosure(value))
}

internal fun luaError(value: String): Varargs = LuaValue.varargsOf(LuaValue.NIL, LuaValue.valueOf(value))