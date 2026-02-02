package li.cil.oc.server.machine.luac

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.machine.Value
import li.cil.repack.com.naef.jnlua.JavaFunction
import li.cil.repack.com.naef.jnlua.LuaState
import li.cil.repack.com.naef.jnlua.LuaType
import java.util.IdentityHashMap

internal fun LuaState.luaError(message: String): Int {
    this.pushNil()
    this.pushString(message)
    return 2
}

internal fun LuaState.pushAny(value: Any?, memo: IdentityHashMap<Any, Int> = IdentityHashMap()) {
    val cached = memo[value]
    if (cached != null) {
        pushValue(cached)
        return
    }

    val recursive = memo.size > 0
    val oldTop = top

    val value: Any? = when (value) {
        is Number -> value
        is Any -> value
        null -> null
        else -> value
    }

    when (value) {
        null, Unit -> pushNil()
        is Boolean -> pushBoolean(value)
        // pushInteger
        is Byte -> pushInteger(value.toLong())
        is Short -> pushInteger(value.toLong())
        is Int -> pushInteger(value.toLong())
        is Long -> pushInteger(value)
        // pushNumber
        is Float -> pushNumber(value.toDouble())
        is Double -> pushNumber(value)
        // pushString
        is Char -> pushString(value.toString())
        is String -> pushString(value)
        is ByteArray -> pushByteArray(value)
        is Array<*> -> pushList(value, value.asIterable(), memo)
        is Value -> if (Settings.get.allowUserdata) {
            pushJavaObjectRaw(value)
        } else pushNil()
        is Iterable<*> -> pushList(value, value, memo)
        is Map<*, *> -> pushTable(value, value, memo)
        else -> {
            OpenComputers.log.warn("Tried to push an unsupported value of type to Lua: " + value.javaClass.name + ".")
            pushNil()
        }
    }
    // Remove values kept on the stack for memoization if this is the
    // original call (not a recursive one, where we might need the memo
    // info even after returning).
    if (!recursive) {
        this.top = oldTop + 1
    }
}

internal fun LuaState.pushList(list: Iterable<Any?>) = pushList(list, list, IdentityHashMap())
private fun LuaState.pushList(obj: Any, list: Iterable<Any?>, memo: IdentityHashMap<Any, Int>) {
    newTable()
    val tableIndex = top
    memo[obj] = tableIndex
    var count = 0
    list.forEachIndexed { index, value ->
        pushAny(value, memo)
        rawSet(tableIndex, index + 1)
        count++
    }
    // Bring table back to top (in case memo values were pushed).
    pushValue(tableIndex)
}

internal fun LuaState.pushTable(map: Map<*, *>, memo: IdentityHashMap<Any, Int> = IdentityHashMap()) = pushTable(map, map, memo)
private fun LuaState.pushTable(obj: Any, map: Map<*, *>, memo: IdentityHashMap<Any, Int>) {
    newTable(0, map.size)
    val tableIndex = top
    memo[obj] = tableIndex
    for ((key, value) in map) {
        if (key != null && key != Unit) {
            pushAny(key, memo)
            val keyIndex = top
            pushAny(value, memo)
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

private fun LuaState.toSimpleJavaObject(index: Int): Any? = when (type(index)) {
    LuaType.BOOLEAN -> toBoolean(index)
    LuaType.NUMBER -> if (isInteger(index)) toInteger(index) else toNumber(index)
    LuaType.STRING -> toByteArray(index)
    LuaType.TABLE -> toJavaObject(index, java.util.Map::class.java)
    LuaType.USERDATA -> toJavaObjectRaw(index)
    else -> null
}

internal fun LuaState.toSimpleJavaObjects(start: Int): List<Any?> =
    (start..top).map { toSimpleJavaObject(it) }
