package li.cil.oc.util

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.machine.Value
import li.cil.repack.com.naef.jnlua.JavaFunction
import li.cil.repack.com.naef.jnlua.LuaState
import li.cil.repack.com.naef.jnlua.LuaType
import java.util.IdentityHashMap

fun LuaState.pushScalaFunction(f: (LuaState) -> Int) {
    pushJavaFunction(JavaFunction { state -> f(state) })
}

fun LuaState.pushValue(value: Any?, memo: IdentityHashMap<Any, Int> = IdentityHashMap()) {
    val recursive = memo.size > 0
    val oldTop = top
    if (memo.containsKey(value)) {
        pushValue(memo[value]!!)
    } else {
        val normalizedValue: Any? = when (value) {
            is Number -> value
            is AnyRef -> value
            null -> null
            else -> value
        }
        when (normalizedValue) {
            null, Unit -> pushNil()
            is Boolean -> pushBoolean(normalizedValue)
            is Byte -> pushInteger(normalizedValue.toLong())
            is Char -> pushString(normalizedValue.toString())
            is Short -> pushInteger(normalizedValue.toLong())
            is Int -> pushInteger(normalizedValue.toLong())
            is Long -> pushInteger(normalizedValue)
            is Float -> pushNumber(normalizedValue.toDouble())
            is Double -> pushNumber(normalizedValue)
            is String -> pushString(normalizedValue)
            is ByteArray -> pushByteArray(normalizedValue)
            is Array<*> -> pushList(normalizedValue, normalizedValue.withIndex().iterator(), memo)
            is Value -> if (Settings.get.allowUserdata) pushJavaObjectRaw(normalizedValue) else pushNil()
            is Iterable<*> -> pushList(normalizedValue, normalizedValue.withIndex().iterator(), memo)
            is Map<*, *> -> pushTable(normalizedValue, normalizedValue, memo)
            else -> {
                OpenComputers.log.warn("Tried to push an unsupported value of type to Lua: " + normalizedValue.javaClass.name + ".")
                pushNil()
            }
        }
        // Remove values kept on the stack for memoization if this is the
        // original call (not a recursive one, where we might need the memo
        // info even after returning).
        if (!recursive) {
            setTop(oldTop + 1)
        }
    }
}

fun LuaState.pushList(obj: Any, list: Iterator<IndexedValue<Any?>>, memo: IdentityHashMap<Any, Int>) {
    newTable()
    val tableIndex = top
    memo[obj] = tableIndex
    var count = 0
    list.forEach { (index, value) ->
        pushValue(value, memo)
        rawSet(tableIndex, index + 1)
        count++
    }
    // Bring table back to top (in case memo values were pushed).
    pushValue(tableIndex)
}

fun LuaState.pushTable(obj: Any, map: Map<*, *>, memo: IdentityHashMap<Any, Int>) {
    newTable(0, map.size)
    val tableIndex = top
    memo[obj] = tableIndex
    for ((key, value) in map) {
        if (key != null && key != Unit) {
            pushValue(key, memo)
            val keyIndex = top
            pushValue(value, memo)
            // Bring key to front, in case of memo from value push.
            // Cannot actually move because that might shift memo info.
            pushValue(keyIndex)
            insert(-2)
            setTable(tableIndex)
        }
    }
    // Bring table back to top (in case memo values were pushed).
    pushValue(tableIndex)
}

fun LuaState.toSimpleJavaObject(index: Int): Any? = when (type(index)) {
    LuaType.BOOLEAN -> toBoolean(index)
    LuaType.NUMBER -> if (isInteger(index)) toInteger(index) else toNumber(index)
    LuaType.STRING -> toByteArray(index)
    LuaType.TABLE -> toJavaObject(index, java.util.Map::class.java)
    LuaType.USERDATA -> toJavaObjectRaw(index)
    else -> null
}

fun LuaState.toSimpleJavaObjects(start: Int): List<Any?> =
    (start..top).map { toSimpleJavaObject(it) }
