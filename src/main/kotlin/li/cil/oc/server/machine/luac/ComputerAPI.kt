package li.cil.oc.server.machine.luac

import li.cil.oc.server.machine.ComputerApi.energy
import li.cil.oc.server.machine.ComputerApi.getArchitecture
import li.cil.oc.server.machine.ComputerApi.getArchitectures
import li.cil.oc.server.machine.ComputerApi.maxEnergy
import li.cil.oc.server.machine.ComputerApi.setArchitecture
import li.cil.oc.server.machine.UnknownArchitectureException
import java.util.*

class ComputerAPI(owner: NativeLuaArchitecture): NativeLuaAPI(owner) {
  override fun initialize() {
    // Computer API, stuff that kinda belongs to os, but we don't want to
    // clutter it.
    lua.newTable()

    // Allow getting the real world time for timeouts.
    lua.pushClosure { lua ->
      lua.pushNumber(System.currentTimeMillis() / 1000.0)
      1
    }
    lua.setField(-2, "realTime")

    // The time the computer has been running, as opposed to the CPU time.
    lua.pushClosure { lua ->
      lua.pushNumber(machine.upTime())
      1
    }
    lua.setField(-2, "uptime")

    // Allow the computer to figure out its own id in the component network.
    lua.pushClosure { lua ->
      val address = node.address()
      if (address == null) lua.pushNil()
      else lua.pushString(address)
      1
    }
    lua.setField(-2, "address")

    lua.pushClosure { lua ->
      // This is *very* unlikely, but still: avoid this getting larger than
      // what we report as the total memory.
      lua.pushInteger((lua.freeMemory.coerceAtMost(lua.totalMemory - owner.kernelMemory) / owner.ramScale).toLong())
      1
    }
    lua.setField(-2, "freeMemory")

    // Allow the system to read how much memory it uses and has available.
    lua.pushClosure { lua ->
      lua.pushInteger(((lua.totalMemory - owner.kernelMemory) / owner.ramScale).toLong())
      1
    }
    lua.setField(-2, "totalMemory")

    lua.pushClosure { lua ->
      lua.pushBoolean(machine.signal(lua.checkString(1), *lua.toSimpleJavaObjects(2).toTypedArray()))
      1
    }
    lua.setField(-2, "pushSignal")

    // And it's /tmp address...
    lua.pushClosure { lua ->
      val address = machine.tmpAddress()
      if (address == null) lua.pushNil()
      else lua.pushString(address)
      1
    }
    lua.setField(-2, "tmpAddress")

    // User management.
    lua.pushClosure { lua ->
      val users = machine.users()
      users.forEach(lua::pushString)
      users.size
    }
    lua.setField(-2, "users")

    lua.pushClosure { lua ->
      val user = lua.checkString(1)
      try {
        machine.addUser(user)
        lua.pushBoolean(true)
        1
      } catch (e: Exception) {
        lua.pushNil()
        lua.pushString(e.message ?: e.toString())
        2
      }
    }
    lua.setField(-2, "addUser")

    lua.pushClosure { lua ->
      lua.pushBoolean(machine.removeUser(lua.checkString(1)))
      1
    }
    lua.setField(-2, "removeUser")

    lua.pushClosure { lua ->
      lua.pushNumber(energy())
      1
    }
    lua.setField(-2, "energy")

    lua.pushClosure { lua ->
      lua.pushNumber(maxEnergy())
      1
    }
    lua.setField(-2, "maxEnergy")

    lua.pushClosure { lua ->
      val a = getArchitectures()
      lua.pushList(
        a,
        a.withIndex().iterator(),
        IdentityHashMap()
      )
      1
    }
    lua.setField(-2, "getArchitectures")

    lua.pushClosure { lua ->
      val arch = getArchitecture() ?: return@pushClosure 0
      lua.pushString(arch)
      1
    }
    lua.setField(-2, "getArchitecture")

    lua.pushClosure { lua ->
      val archName = lua.checkString(1)
      try {
        val result = setArchitecture(archName) ?: return@pushClosure 0
        lua.pushBoolean(result)
        1
      } catch (e: UnknownArchitectureException) {
        lua.luaError("unknown architecture")
      }
    }
    lua.setField(-2, "setArchitecture")

    // Set the computer table.
    lua.setGlobal("computer")
  }
}
