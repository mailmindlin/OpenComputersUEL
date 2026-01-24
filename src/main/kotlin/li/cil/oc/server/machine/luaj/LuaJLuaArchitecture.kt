package li.cil.oc.server.machine.luaj

import li.cil.oc.OpenComputers
import li.cil.oc.api.machine.Architecture
import li.cil.oc.api.machine.ExecutionResult
import li.cil.oc.server.machine.*
import li.cil.oc.server.machine.luaj.LuaClosure.Companion.toLuaValue
import li.cil.oc.util.mapArray
import li.cil.repack.org.luaj.vm2.*
import li.cil.repack.org.luaj.vm2.lib.jse.JsePlatform
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import li.cil.oc.api.machine.Machine as ApiMachine

@Architecture.Name("LuaJ")
class LuaJLuaArchitecture(machine: ApiMachine): GenericLuaArchitecture(machine) {
  internal var lua: Globals? = null
  private var thread: LuaThread? = null
  private var synchronizedCall: LuaFunction? = null
  private var synchronizedResult: LuaValue? = null
  private var doneWithInitRun = false

  internal var memory: Int = 0

  companion object {
    @JvmStatic
    private fun InvokeResult.toVarargs(): Varargs {
      return when (this) {
        // Success types
        InvokeResult.Void -> LuaValue.TRUE
        is InvokeResult.Success -> LuaValue.varargsOf(arrayOf(LuaValue.TRUE, *results.mapArray { it.toLuaValue() }))
        // Error types
        InvokeResult.LimitReached -> LuaValue.NONE
        is InvokeResult.ErrorMessage ->
          if (!this.args3)
            LuaValue.varargsOf(LuaValue.FALSE, LuaValue.valueOf(message))
          else
            //TODO: stack trace?
            LuaValue.varargsOf(LuaValue.TRUE, LuaValue.NIL, LuaValue.valueOf(message))
      }
    }
    @JvmStatic
    private fun DocumentationResult.toVarargs(): Varargs {
      return when (this) {
        DocumentationResult.Empty -> LuaValue.NIL
        is DocumentationResult.Documentation -> LuaValue.valueOf(this.text)
        is DocumentationResult.Error -> LuaValue.varargsOf(LuaValue.NIL, LuaValue.valueOf(this.message))
      }
    }
  }

  private val apis = arrayOf(
    ComponentAPI(this),
    ComputerAPI(this),
    OSAPI(this),
    SystemAPI(this),
    UnicodeAPI(this),
    UserdataAPI(this),
  )

  internal inline fun invoke(f: () -> Array<Any?>?): Varargs
    = invokeGeneric(f).toVarargs()

  internal inline fun documentation(f: () -> String?): Varargs
    = documentationGeneric(f).toVarargs()

  // ----------------------------------------------------------------------- //

  override fun isInitialized(): Boolean = doneWithInitRun

  override fun recomputeMemory(components: Iterable<ItemStack>): Boolean {
    memory = memoryInBytes(components)
    return memory > 0
  }

  // ----------------------------------------------------------------------- //

  override fun runSynchronized() {
    synchronizedResult = synchronizedCall?.call()
    synchronizedCall = null
  }

  override fun runThreaded(isSynchronizedReturn: Boolean): ExecutionResult {
    val thread = thread!!
    return try {
      // Resume the Lua state and remember the number of results we get.
      val results = if (isSynchronizedReturn) {
        // If we were doing a synchronized call, continue where we left off.
        val result = thread.resume(synchronizedResult)
        synchronizedResult = null
        result
      } else {
        if (!doneWithInitRun) {
          // We're doing the initialization run.
          val result = thread.resume(LuaValue.NONE)
          // Mark as done *after* we ran, to avoid switching to synchronized
          // calls when we actually need direct ones in the init phase.
          doneWithInitRun = true
          // We expect to get nothing here, if we do we had an error.
          if (result.narg() != 1) {
            result
          } else {
            // Fake zero sleep to avoid stopping if there are no signals.
            LuaValue.varargsOf(LuaValue.TRUE, LuaValue.valueOf(0))
          }
        } else when (val signal = machine.popSignal()) {
          null -> thread.resume(LuaValue.NONE)
          else -> thread.resume(LuaValue.varargsOf(arrayOf<LuaValue>(LuaValue.valueOf(signal.name())) + signal.args().mapArray { it.toLuaValue() }))
        }
      }

      // Check if the kernel is still alive.
      if (thread.state.status == LuaThread.STATUS_SUSPENDED) {
        // If we get one function it must be a wrapper for a synchronized
        // call. The protocol is that a closure is pushed that is then called
        // from the main server thread, and returns a table, which is in turn
        // passed to the originating coroutine.yield().
        if (results.narg() == 2 && results.isfunction(2)) {
          synchronizedCall = results.checkfunction(2)
          ExecutionResult.SynchronizedCall()
        }
        // Check if we are shutting down, and if so if we're rebooting. This
        // is signalled by boolean values, where `false` means shut down,
        // `true` means reboot (i.e shutdown then start again).
        else if (results.narg() == 2 && results.type(2) == LuaValue.TBOOLEAN) {
          ExecutionResult.Shutdown(results.toboolean(2))
        }
        else {
          // If we have a single number, that's how long we may wait before
          // resuming the state again. Note that the sleep may be interrupted
          // early if a signal arrives in the meantime. If we have something
          // else we just process the next signal or wait for one.
          val ticks = if (results.narg() == 2 && results.isnumber(2)) (results.todouble(2) * 20).toInt() else Int.MAX_VALUE
          ExecutionResult.Sleep(ticks)
        }
      }
      // The kernel thread returned. If it threw we'd be in the catch below.
      else {
        // This is a little... messy because we run a pcall inside the kernel
        // to be able to catch errors before JNLua gets its claws on them. So
        // we can either have (boolean, string | error) if the main kernel
        // fails, or (boolean, boolean, string | error) if something inside
        // that pcall goes bad.
        val isInnerError = results.type(2) == LuaValue.TBOOLEAN && (results.isstring(3) || results.isnoneornil(3))
        val isOuterError = results.isstring(2) || results.isnoneornil(2)
        if (results.type(1) != LuaValue.TBOOLEAN || !isInnerError || !isOuterError) {
          OpenComputers.log.warn("Kernel returned unexpected results.")
        }
        // The pcall *should* never return normally... but check for it nonetheless.
        if ((isOuterError && results.toboolean(1)) || (isInnerError && results.toboolean(2))) {
          OpenComputers.log.warn("Kernel stopped unexpectedly.")
          ExecutionResult.Shutdown(false)
        }
        else {
          val error =
            if (isInnerError)
              if (results.isuserdata(3)) results.touserdata(3).toString()
              else results.tojstring(3)
            else if (results.isuserdata(2)) results.touserdata(2).toString()
            else results.tojstring(2)
          ExecutionResult.Error(error ?: "unknown error")
        }
      }
    } catch (e: LuaError) {
      OpenComputers.log.warn("Kernel crashed. This is a bug!", e)
      ExecutionResult.Error("kernel panic: this is a bug, check your log file and report it")
    } catch (e: Exception) {
      OpenComputers.log.warn("Unexpected error in kernel. This is a bug!", e)
      ExecutionResult.Error("kernel panic: this is a bug, check your log file and report it")
    }
  }

  // ----------------------------------------------------------------------- //

  override fun initialize(): Boolean {
    val lua = JsePlatform.debugGlobals()
    this.lua = lua
    lua.set("package", LuaValue.NIL)
    lua.set("require", LuaValue.NIL)
    lua.set("io", LuaValue.NIL)
    lua.set("os", LuaValue.NIL)
    lua.set("luajava", LuaValue.NIL)

    // Remove some other functions we don't need and are dangerous.
    lua.set("dofile", LuaValue.NIL)
    lua.set("loadfile", LuaValue.NIL)

    apis.forEach(LuaJAPI::initialize)

    recomputeMemory(machine.host().internalComponents())

    val kernel = lua.load(this.machineScript(), "=machine", "t", lua)
    thread = LuaThread(lua, kernel) // Left as the first value on the stack.

    return true
  }

  override fun close() {
    lua = null
    thread = null
    synchronizedCall = null
    synchronizedResult = null
    doneWithInitRun = false
  }

  // ----------------------------------------------------------------------- //

  override fun load(nbt: NBTTagCompound) {
    if (machine.isRunning) {
      machine.stop()
      machine.start()
    }
  }

  override fun save(nbt: NBTTagCompound) {}
}
