package li.cil.oc.server.machine.luaj

//import li.cil.oc.api
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Machine
import li.cil.oc.api.driver.item.MutableProcessor
import li.cil.oc.api.driver.item.Processor
import li.cil.oc.api.network.Connector
import li.cil.oc.server.machine.ComputerApi.energy
import li.cil.oc.server.machine.ComputerApi.maxEnergy
import li.cil.oc.server.machine.ComputerApi.getArchitecture
import li.cil.oc.server.machine.ComputerApi.getArchitectures
import li.cil.oc.server.machine.ComputerApi.setArchitecture
import li.cil.oc.server.machine.UnknownArchitectureException
import li.cil.oc.util.LuaClosure.Companion.toSimpleJavaObjects
import li.cil.repack.org.luaj.vm2.LuaValue

internal class ComputerAPI(owner: LuaJLuaArchitecture): LuaJAPI(owner) {
  override fun initialize() {
    // Computer API, stuff that kinda belongs to os, but we don't want to
    // clutter it.
    val computer = LuaValue.tableOf()

    // Allow getting the real world time for timeouts.
    computer.setClosure("realTime") { LuaValue.valueOf(System.currentTimeMillis() / 1000.0) }

    computer.setClosure("uptime") { LuaValue.valueOf(machine.upTime()) }

    // Allow the computer to figure out its own id in the component network.
    computer.setClosure("address") { node.address()?.let(LuaValue::valueOf) ?: LuaValue.NIL }

    computer.setClosure("freeMemory") { LuaValue.valueOf(owner.memory / 2) }

    computer.setClosure("totalMemory") { LuaValue.valueOf(owner.memory) }

    computer.setClosure("pushSignal") { args -> LuaValue.valueOf(machine.signal(args.checkjstring(1), *toSimpleJavaObjects(args, 2).toTypedArray())) }

    // And it's /tmp address...
    computer.setClosure("tmpAddress") {
      val address = machine.tmpAddress()
      if (address == null) LuaValue.NIL
      else LuaValue.valueOf(address)
    }

    // User management.
    computer.setClosure("users") { LuaValue.varargsOf(machine.users().map(LuaValue::valueOf).toTypedArray()) }

    computer.setClosure("addUser") { args ->
      machine.addUser(args.checkjstring(1))
      LuaValue.TRUE
    }

    computer.setClosure("removeUser") { args -> LuaValue.valueOf(machine.removeUser(args.checkjstring(1))) }

    computer.setClosure("energy") { LuaValue.valueOf(energy()) }

    computer.setClosure("maxEnergy") { LuaValue.valueOf(maxEnergy()) }

    computer.setClosure("getArchitectures") { args ->
      LuaValue.listOf(
        getArchitectures()
        .map(LuaValue::valueOf)
        .toList()
        .toTypedArray()
      )
    }

    computer.setClosure("getArchitecture") { args ->
        getArchitecture()
          ?.let(LuaValue::valueOf)
          ?: LuaValue.NONE
    }

    computer.setClosure("setArchitecture") { args ->
      val archName = args.checkjstring(1)
      try {
        setArchitecture(archName)
          ?.let(LuaValue::valueOf)
          ?: LuaValue.NONE
      } catch (e: UnknownArchitectureException) {
        luaError("unknown architecture")
      }
    }

    // Set the computer table.
    lua.set("computer", computer)
  }
}
