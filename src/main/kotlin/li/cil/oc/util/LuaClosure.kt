package li.cil.oc.util

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.machine.Value
import li.cil.repack.org.luaj.vm2.LuaString
import li.cil.repack.org.luaj.vm2.LuaValue
import li.cil.repack.org.luaj.vm2.Varargs
import li.cil.repack.org.luaj.vm2.lib.VarArgFunction

class LuaClosure(val f: (Varargs) -> Varargs) : VarArgFunction() {
    override fun invoke(args: Varargs): Varargs = f(args)

    companion object {
        @JvmStatic
        fun wrapClosure(f: (Varargs) -> LuaValue): LuaClosure = LuaClosure { args ->
            when (val result = f(args)) {
                is Varargs -> result
                LuaValue.NONE -> LuaValue.NONE
                else -> LuaValue.varargsOf(arrayOf(result))
            }
        }

        @JvmStatic
        fun wrapVarArgClosure(f: (Varargs) -> Varargs): LuaClosure = LuaClosure(f)

        @JvmStatic
        fun toLuaValue(value: Any?): LuaValue {
            val normalizedValue: Any? = when (value) {
                is Number -> value
                is Any -> value
                null -> null
                else -> value
            }
            return when (normalizedValue) {
                null, Unit -> LuaValue.NIL
                is Boolean -> LuaValue.valueOf(normalizedValue)
                is Byte -> LuaValue.valueOf(normalizedValue.toInt())
                is Char -> LuaValue.valueOf(normalizedValue.toString())
                is Short -> LuaValue.valueOf(normalizedValue.toInt())
                is Int -> LuaValue.valueOf(normalizedValue)
                is Long -> LuaValue.valueOf(normalizedValue.toDouble())
                is Float -> LuaValue.valueOf(normalizedValue.toDouble())
                is Double -> LuaValue.valueOf(normalizedValue)
                is String -> LuaValue.valueOf(normalizedValue)
                is ByteArray -> LuaValue.valueOf(normalizedValue)
                is Array<*> -> toLuaList(normalizedValue.toList())
                is Value -> if (Settings.get.allowUserdata) LuaValue.userdataOf(normalizedValue) else LuaValue.NIL
                is Iterable<*> -> toLuaList(normalizedValue)
                is Map<*, *> -> toLuaTable(normalizedValue)
                else -> {
                    OpenComputers.log.warn("Tried to push an unsupported value of type to Lua: ${normalizedValue.javaClass.name}.")
                    LuaValue.NIL
                }
            }
        }

        @JvmStatic
        fun toLuaList(value: Iterable<*>): LuaValue {
            return LuaValue.listOf(value.map { toLuaValue(it) }.toTypedArray())
        }

        @JvmStatic
        fun toLuaTable(value: Map<*, *>): LuaValue {
            return LuaValue.tableOf(value.flatMap { (k, v) ->
                listOf(toLuaValue(k), toLuaValue(v))
            }.toTypedArray())
        }

        @JvmStatic
        fun toSimpleJavaObject(value: LuaValue): Any? = when (value.type()) {
            LuaValue.TBOOLEAN -> value.toboolean()
            LuaValue.TNUMBER -> value.todouble()
            LuaValue.TSTRING -> when (value) {
                is LuaString -> value.m_bytes.copyOfRange(value.m_offset, value.m_offset + value.m_length)
                else -> value.tojstring() // Waddafaq?
            }
            LuaValue.TTABLE -> {
                val table = value.checktable()
                table.keys().associate { key -> toSimpleJavaObject(key) to toSimpleJavaObject(table.get(key)) }
            }
            LuaValue.TUSERDATA -> value.touserdata()
            else -> null
        }

        @JvmStatic
        @JvmOverloads
        fun toSimpleJavaObjects(args: Varargs, start: Int = 1): List<Any?> =
            (start..args.narg()).map { toSimpleJavaObject(args.arg(it)) }
    }
}
