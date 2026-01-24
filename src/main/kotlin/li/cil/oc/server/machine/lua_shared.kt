/**
 * Shared functions between LuaC and LuaJ
 */
package li.cil.oc.server.machine

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.item.Memory
import li.cil.oc.api.machine.Architecture
import li.cil.oc.api.machine.LimitReachedException
import li.cil.oc.api.machine.Machine
import li.cil.oc.util.notEmpty
import net.minecraft.item.ItemStack
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

internal sealed interface InvokeResult {
    object LimitReached: InvokeResult
    object Void: InvokeResult
    class Success(val results: Array<out Any?>): InvokeResult
    data class ErrorMessage(val message: String, val args3: Boolean, val stackTrace: String? = null): InvokeResult {
        companion object {
            @JvmStatic
            fun error2(message: String): ErrorMessage = ErrorMessage(message, false)
            @JvmStatic
            fun error3(message: String): ErrorMessage = ErrorMessage(message, true)
        }
    }
}

private fun mapIllegalArgument(e: IllegalArgumentException): InvokeResult.ErrorMessage {
    if (Settings.get.logLuaCallbackErrors) {
        OpenComputers.log.warn("Exception in Lua callback.", e)
    }
    return InvokeResult.ErrorMessage.error2(e.message ?: "bad argument")
}

private fun mapException(e: Exception): InvokeResult.ErrorMessage {
    assert(e !is LimitReachedException)
    assert(e !is IllegalArgumentException)
    if (Settings.get.logLuaCallbackErrors) {
        OpenComputers.log.warn("Exception in Lua callback.", e)
    }
    e.message?.let { message ->
        return@mapException if (Settings.get.logLuaCallbackErrors) {
            InvokeResult.ErrorMessage(message, true, e.stackTrace.joinToString("", prefix="\n", postfix = "\n"))
        } else {
            InvokeResult.ErrorMessage(message, true)
        }
    }
    return when (e) {
        is IndexOutOfBoundsException -> InvokeResult.ErrorMessage.error2("index out of bounds")
//        is IllegalArgumentException -> InvokeResult.ErrorMessage.error2("bad argument")
        is NoSuchMethodException -> InvokeResult.ErrorMessage.error2("no such method")
        is FileNotFoundException -> InvokeResult.ErrorMessage.error3("file not found")
        is SecurityException -> InvokeResult.ErrorMessage.error3("access denied")
        is IOException -> InvokeResult.ErrorMessage.error3("i/o error")
        else -> {
            OpenComputers.log.warn("Unexpected error in Lua callback.", e)
            InvokeResult.ErrorMessage.error3("unknown error")
        }
    }
}

/**
 * Generic form of invoke() for LuaC and LuaJ
 */
internal inline fun invokeGeneric(f: () -> Array<out Any?>?): InvokeResult {
    return try {
        when (val results = f()) {
            is Array<*> -> InvokeResult.Success(results)
            null -> InvokeResult.Void
            else -> TODO() // Not reachable
        }
    } catch (e: LimitReachedException) {
        InvokeResult.LimitReached
    } catch (e: IllegalArgumentException) {
        InvokeResult.ErrorMessage.error2(e.message ?: "bad argument")
    } catch (e: Exception) {
        mapException(e)
    }
}

internal sealed interface DocumentationResult {
    object Empty: DocumentationResult
    data class Documentation(val text: String): DocumentationResult {
        init {
            assert(text.isNotEmpty())
        }
    }
    data class Error(val message: String): DocumentationResult
}

internal inline fun documentationGeneric(f: () -> String?): DocumentationResult {
    return try {
        f()
            ?.takeIf(String::isNotEmpty)
            ?.let(DocumentationResult::Documentation)
            ?: DocumentationResult.Empty
    } catch (e: NoSuchMethodException) {
        DocumentationResult.Error("no such method")
    } catch (e: Exception) { // In original this was Throwable
        DocumentationResult.Error(e.message ?: e.toString())
    }
}

abstract class GenericLuaArchitecture(val machine: Machine): Architecture {
    companion object {
        /** Get the memory provided by `this` stack as a component */
        @JvmStatic
        private fun ItemStack.providedMemory(): Double {
            val stack = this.notEmpty() ?: return 0.0
            val driver = stack.let(Driver::driverFor) as? Memory ?: return 0.0
            return driver.amount(stack)
        }
    }

    protected fun memoryInBytes(components: Iterable<ItemStack>): Int {
        val memory = components.sumOf { it.providedMemory() } * 1024
        return memory.toInt().coerceIn(0 .. Settings.get.maxTotalRam)
    }

    protected fun machineScript(): InputStream?
        = Machine::class.java.getResourceAsStream(Settings.scriptPath + "machine.lua")

    override fun onSignal() {}
    override fun onConnect() {}
}