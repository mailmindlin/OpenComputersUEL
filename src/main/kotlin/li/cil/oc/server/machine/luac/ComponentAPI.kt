package li.cil.oc.server.machine.luac

import li.cil.oc.api.network.Component

class ComponentAPI(owner: NativeLuaArchitecture): NativeLuaAPI(owner) {
  override fun initialize() {
    lua.newTable()

    lua.pushClosure { lua ->
      synchronized(components) {
        val filter = if (lua.isString(1)) lua.toString(1) else null
        val exact = if (lua.isBoolean(2)) lua.toBoolean(2) else true
        lua.newTable(0, components.size)
        fun matches (name: String) = if (exact) name == filter!! else name.contains(filter!!)
        for ((address, name) in components) {
        if (filter?.isEmpty() != false || matches(name)) {
          lua.pushString(address)
          lua.pushString(name)
          lua.rawSet(-3)
        }
      }
        1
      }
    }
    lua.setField(-2, "list")

    lua.pushClosure { lua ->
      synchronized(components) {
        val name = components[lua.checkString(1)] ?: return@synchronized lua.luaError("no such component")
        lua.pushString(name)
        1
      }
    }
    lua.setField(-2, "type")

    lua.pushClosure { lua ->
      synchronized(components) {
        val address = lua.checkString(1)
        val name = components[address] ?: return@synchronized lua.luaError("no such component")
        lua.pushInteger(owner.machine.host().componentSlot(address).toLong())
        1
      }
    }
    lua.setField(-2, "slot")

    lua.pushClosure { lua ->
      withComponent(lua.checkString(1)) { component ->
        lua.newTable()
        for ((name, annotation) in machine.methods(component.host())) {
          lua.pushString(name)
          lua.newTable()
          lua.pushBoolean(annotation.direct)
          lua.setField(-2, "direct")
          lua.pushBoolean(annotation.getter)
          lua.setField(-2, "getter")
          lua.pushBoolean(annotation.setter)
          lua.setField(-2, "setter")
          lua.rawSet(-3)
        }
        1
      }
    }
    lua.setField(-2, "methods")

    lua.pushClosure { lua ->
      val address = lua.checkString(1)
      val method = lua.checkString(2)
      val args = lua.toSimpleJavaObjects(3)
      owner.invoke { machine.invoke(address, method, args.toTypedArray()) }
    }
    lua.setField(-2, "invoke")

    lua.pushClosure { lua ->
      withComponent(lua.checkString(1)) { component ->
        val method = lua.checkString(2)
        val methods = machine.methods(component.host())
        owner.documentation { methods.get(method)?.doc }
      }
    }
    lua.setField(-2, "doc")

    lua.setGlobal("component")
  }

  private fun withComponent(address: String, f: (Component) -> Int): Int {
    val component = node.network().node(address)
    return if (component != null && component is Component && (component.canBeReachedFrom(node) || component == node)) {
      f(component)
    } else {
      lua.pushNil()
      lua.pushString("no such component")
      2
    }
  }
}
