package li.cil.oc.server.machine.luac

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.machine.Architecture
import li.cil.oc.api.machine.ExecutionResult
import li.cil.oc.api.machine.Machine
import li.cil.oc.common.SaveHandler
import li.cil.oc.server.machine.*
import li.cil.oc.server.machine.Machine.State as MachineState
import li.cil.repack.com.naef.jnlua.*
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.realms.RealmsMth.ceil

@Architecture.Name("Lua 5.2")
class NativeLua52Architecture(machine: Machine): NativeLuaArchitecture(machine) {
  override val factory get() = LuaStateFactory.Lua52
}

@Architecture.Name("Lua 5.3")
class NativeLua53Architecture(machine: Machine): NativeLuaArchitecture(machine) {
  override val factory get() = LuaStateFactory.Lua53
}

@Architecture.Name("Lua 5.4")
class NativeLua54Architecture(machine: Machine): NativeLuaArchitecture(machine) {
  override val factory get() = LuaStateFactory.Lua54
}

sealed class NativeLuaArchitecture(machine: Machine): GenericLuaArchitecture(machine) {
  protected abstract val factory: LuaStateFactory
  internal var lua: LuaState? = null
  internal var kernelMemory = 0
  internal var ramScale: Double = 1.0
  private val persistence = PersistenceAPI(this)

  private val apis = arrayOf(
    ComponentAPI(this),
    ComputerAPI(this),
    OSAPI(this),
    SystemAPI(this),
    UnicodeAPI(this),
    UserdataAPI(this),
    // Persistence has to go last to ensure all other APIs can go into the permanent value table.
    persistence)

  private fun InvokeResult.pushLua(): Int {
    val lua = lua!!
    return when (this) {
      // Success
      InvokeResult.Void -> {
        lua.pushBoolean(true)
        1
      }
      is InvokeResult.Success -> {
        lua.pushBoolean(true)
        this.results.forEach(lua::pushValue)
        1 + this.results.size
      }
      // Errors
      InvokeResult.LimitReached -> 0
      is InvokeResult.ErrorMessage -> {
        if (!this.args3) {
          lua.pushBoolean(false)
          lua.pushString(this.message)
          return 2
        }
        lua.pushBoolean(true)
        lua.pushNil()
        lua.pushString(this.message)
        if (this.stackTrace == null)
          return 3
        lua.pushString(this.stackTrace)
        return 4
      }
    }
  }

  private fun DocumentationResult.pushLua(): Int {
    val lua = lua!!
    return when (this) {
      is DocumentationResult.Documentation -> {
        lua.pushString(this.text)
        return 1
      }
      DocumentationResult.Empty -> {
        lua.pushNil()
        return 1
      }
      is DocumentationResult.Error -> {
        lua.pushNil()
        lua.pushString(this.message)
        return 2
      }
    }
  }

  internal fun invoke(f: () -> Array<out Any?>?): Int
    = invokeGeneric(f).pushLua()

  internal fun documentation(f: () -> String?): Int
    = documentationGeneric(f).pushLua()

  // ----------------------------------------------------------------------- //

  override fun isInitialized(): Boolean = kernelMemory > 0

  override fun recomputeMemory(components: Iterable<ItemStack>): Boolean {
    val lua = lua
    val memoryBytes = memoryInBytes(components)
    if (lua != null && Settings.get.limitMemory) {
      lua.totalMemory = Int.MAX_VALUE
      if (kernelMemory > 0) {
        lua.totalMemory = kernelMemory + ceil(memoryBytes * ramScale).toInt()
      }
    }
    return memoryBytes > 0
  }

  // ----------------------------------------------------------------------- //

  override fun runSynchronized() {
    // These three asserts are all guaranteed by run().
    val lua = lua!!
    assert(lua.top == 2)
    assert(lua.isThread(1))
    assert(lua.isFunction(2))

    try {
      // Synchronized call protocol requires the called function to return
      // a table, which holds the results of the call, to be passed back
      // to the coroutine.yield() that triggered the call.
      lua.call(0, 1)
      lua.checkType(2, LuaType.TABLE)
    } catch(_: LuaMemoryAllocationException) {
      // This can happen if we run out of memory while converting a Java
      // exception to a string (which we have to do to avoid keeping
      // userdata on the stack, which cannot be persisted).
      throw OutOfMemoryError("not enough memory")
    }
  }

  override fun runThreaded(isSynchronizedReturn: Boolean): ExecutionResult {
    val lua = lua!!
    return try {
      // The kernel thread will always be at stack index one.
      assert(lua.isThread(1))

      // Resume the Lua state and remember the number of results we get.
      val results = if (isSynchronizedReturn) {
        // If we were doing a synchronized call, continue where we left off.
        assert(lua.top == 2)
        assert(lua.isTable(2))
        lua.resume(1, 1)
      } else {
        if (kernelMemory == 0) {
          // We're doing the initialization run.
          if (lua.resume(1, 0) > 0) {
            // We expect to get nothing here, if we do we had an error.
            0
          } else {
            // Run the garbage collector to get rid of stuff left behind after
            // the initialization phase to get a good estimate of the base
            // memory usage the kernel has (including libraries). We remember
            // that size to grant user-space programs a fixed base amount of
            // memory, regardless of the memory need of the underlying system
            // (which may change across releases).
            lua.gc(LuaState.GcAction.COLLECT, 0)
            kernelMemory = (lua.totalMemory - lua.freeMemory).coerceAtLeast(1)
            recomputeMemory(machine.host().internalComponents())

            // Fake zero sleep to avoid stopping if there are no signals.
            lua.pushInteger(0)
            1
          }
        } else {
          when (val signal = machine.popSignal()) {
            null -> lua.resume(1, 0)
            else -> {
              lua.pushString(signal.name())
              signal.args().forEach(lua::pushValue)
              lua.resume(1, 1 + signal.args().size)
            }
          }
        }
      }

      // Check if the kernel is still alive.
      if (lua.status(1) == LuaState.YIELD) {
        // If we get one function it must be a wrapper for a synchronized
        // call. The protocol is that a closure is pushed that is then called
        // from the main server thread, and returns a table, which is in turn
        // passed to the originating coroutine.yield().
        if (results == 1 && lua.isFunction(2)) {
          ExecutionResult.SynchronizedCall()
          // Check if we are shutting down, and if so if we're rebooting. This
          // is signalled by boolean values, where `false` means shut down,
          // `true` means reboot (i.e shutdown then start again).
        } else if (results == 1 && lua.isBoolean(2)) {
          ExecutionResult.Shutdown(lua.toBoolean(2))
        } else {
          // If we have a single number, that's how long we may wait before
          // resuming the state again. Note that the sleep may be interrupted
          // early if a signal arrives in the meantime. If we have something
          // else we just process the next signal or wait for one.
          val ticks =
            if (results == 1 && lua.isNumber(2))
              (lua.toNumber(2) * 20).toInt()
            else
              Int.MAX_VALUE
          lua.pop(results)
          ExecutionResult.Sleep(ticks)
        }
      // The kernel thread returned. If it threw we'd be in the catch below.
      } else {
        assert(lua.isThread(1))
        // We're expecting the result of a pcall, if anything, so boolean + (result | string).
        if (!lua.isBoolean(2) || !(lua.isString(3) || lua.isNoneOrNil(3)))
          OpenComputers.log.warn("Kernel returned unexpected results.")

        // The pcall *should* never return normally... but check for it nonetheless.
        if (lua.toBoolean(2)) {
          OpenComputers.log.warn("Kernel stopped unexpectedly.")
          ExecutionResult.Shutdown(false)
        } else {
          if (Settings.get.limitMemory)
            lua.totalMemory = Int.MAX_VALUE
          val error =
            if (lua.isJavaObjectRaw(3)) lua.toJavaObjectRaw(3).toString()
            else lua.toString(3)
          ExecutionResult.Error(error ?: "unknown error")
        }
      }
    } catch (e: LuaRuntimeException) {
      OpenComputers.log.warn("Kernel crashed. This is a bug!\n" + e.toString() + "\tat " + e.luaStackTrace.joinToString("\n\tat "))
      ExecutionResult.Error("kernel panic: this is a bug, check your log file and report it")
    } catch (e: LuaGcMetamethodException) {
      ExecutionResult.Error("kernel panic:\n${e.message ?: "error in garbage collection metamethod"}")
    } catch (e: LuaMemoryAllocationException) {
      ExecutionResult.Error("not enough memory")
    //TODO: just OutOfMemoryError?
    } catch (e: Error) {
      if (e.message != "not enough memory")
        throw e
      ExecutionResult.Error("not enough memory")
    }
  }

  // ----------------------------------------------------------------------- //

  override fun initialize(): Boolean {
    // Creates a new state with all base libraries and the persistence library
    // loaded into it. This means the state has much more power than it
    // rightfully should have, so we sandbox it a bit in the following.
    val lua = factory.createState() ?: run {
      lua = null
      machine.crash("native libraries not available")
      return@initialize false
    }
    this.lua = lua

    ramScale = if (lua.pointerWidth >= 8) Settings.get.ramScaleFor64Bit else 1.0

    apis.forEach(NativeLuaAPI::initialize)

    lua.load(this.machineScript(), "=machine", "t")
    lua.newThread() // Left as the first value on the stack.

    return true
  }

  override fun close() {
    val lua = lua
    if (lua != null) {
      if (Settings.get.limitMemory) {
        lua.totalMemory = Integer.MAX_VALUE
      }
      lua.close()
    }
    this.lua = null
    kernelMemory = 0
  }

  // ----------------------------------------------------------------------- //

  // Transition to storing the 'are we in or returning from a sync call' in here
  // so we don't need to check the state. Will need a period where saves are
  // loaded using the old *and* new method and saved using the new.
  @Deprecated("transition (see docs)")
  private fun state() = (machine as li.cil.oc.server.machine.Machine).state

  override fun load(nbt: NBTTagCompound) {
    if (!machine.isRunning) return

    val lua = lua!!

    // Unlimit memory use while unpersisting.
    if (Settings.get.limitMemory) {
      lua.setTotalMemory(Integer.MAX_VALUE)
    }

    try {
      // Try unpersisting Lua, because that's what all of the rest depends
      // on. First, clear the stack, meaning the current kernel.
      lua.top = 0

      persistence.unpersist(SaveHandler.load(nbt, machine.node().address() + "_kernel"))
      if (!lua.isThread(1)) {
        // This shouldn't really happen, but there's a chance it does if
        // the save was corrupt (maybe someone modified the Lua files).
        throw LuaRuntimeException("Invalid kernel.")
      }
      if (state().contains(MachineState.SynchronizedCall) || state().contains(MachineState.SynchronizedReturn)) {
        persistence.unpersist(SaveHandler.load(nbt, machine.node().address() + "_stack"))
        if (!(if (state().contains(MachineState.SynchronizedCall)) lua.isFunction(2) else lua.isTable(2))) {
          // Same as with the above, should not really happen normally, but
          // could for the same reasons.
          throw LuaRuntimeException("Invalid stack.")
        }
      }

      kernelMemory = (nbt.getInteger("kernelMemory") * ramScale).toInt()

      apis.forEach { api -> api.load(nbt) }

      try {
        lua.gc(LuaState.GcAction.COLLECT, 0)
      } catch (e: Exception) {
        OpenComputers.log.warn("Error cleaning up loaded computer @ ${machine.host().machinePosition()}. This either means the server is badly overloaded or a user created an evil __gc method, accidentally or not.")
        machine.crash("error in garbage collector, most likely __gc method timed out")
      }
    } catch (e: LuaRuntimeException) {
      throw Exception(e.toString() + (if (e.luaStackTrace.isEmpty()) "" else "\tat " + e.luaStackTrace.joinToString("\n\tat ")), e)
    }

    // Limit memory again.
    recomputeMemory(machine.host().internalComponents())
  }

  override fun save(nbt: NBTTagCompound) {
    // Unlimit memory while persisting.
    val lua = lua!!
    if (Settings.get.limitMemory) {
      lua.setTotalMemory(Integer.MAX_VALUE)
    }

    try {
      // Try persisting Lua, because that's what all of the rest depends on.
      // Save the kernel state (which is always at stack index one).
      assert(lua.isThread(1))

      SaveHandler.scheduleSave(machine.host(), nbt, machine.node().address() + "_kernel", persistence.persist(1))
      // While in a driver call we have one object on the global stack: either
      // the function to call the driver with, or the result of the call.
      if (state().contains(MachineState.SynchronizedCall) || state().contains(MachineState.SynchronizedReturn)) {
        assert(if (state().contains(MachineState.SynchronizedCall)) lua.isFunction(2) else lua.isTable(2))
        SaveHandler.scheduleSave(machine.host(), nbt, machine.node().address() + "_stack", persistence.persist(2))
      }

      nbt.setInteger("kernelMemory", ceil(kernelMemory / ramScale).toInt())

      apis.forEach { api -> api.save(nbt) }

      try {
        lua.gc(LuaState.GcAction.COLLECT, 0)
      } catch (e: Exception) {
        OpenComputers.log.warn("Error cleaning up loaded computer @ ${machine.host().machinePosition()}. This either means the server is badly overloaded or a user created an evil __gc method, accidentally or not.")
        machine.crash("error in garbage collector, most likely __gc method timed out")
      }
    } catch (e: LuaRuntimeException) {
        OpenComputers.log.warn("Could not persist computer @ ${machine.host().machinePosition()}.\n${e}" + (if (e.stackTrace.isEmpty()) "" else "\tat " + e.stackTrace.joinToString("\n\tat ")))
        nbt.removeTag("state")
    } catch (e: LuaGcMetamethodException) {
        OpenComputers.log.warn("Could not persist computer @ ${machine.host().machinePosition()}.\n${e}")
        nbt.removeTag("state")
    }

    // Limit memory again.
    recomputeMemory(machine.host().internalComponents())
  }
}
