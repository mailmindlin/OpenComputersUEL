package li.cil.oc.server.machine.luaj

import li.cil.oc.api.network.Component
import li.cil.oc.util.LuaClosure
import li.cil.oc.util.LuaClosure.Companion.toSimpleJavaObjects
import li.cil.repack.org.luaj.vm2.LuaTable
import li.cil.repack.org.luaj.vm2.LuaValue
import li.cil.repack.org.luaj.vm2.Varargs

class ComponentAPI(owner: LuaJLuaArchitecture): LuaJAPI(owner) {
  override fun initialize() {
    // Component interaction stuff.
    val component: LuaTable = LuaValue.tableOf()

    component.setClosure("list") { args ->
      synchronized(components) {
        val filter = if (args.isstring(1)) args.tojstring(1) else null
        val exact = args.optboolean(2, false)
        val table = LuaValue.tableOf(0, components.size)
        fun matches(name: String) = if (exact) name == filter else name.contains(filter!!)
        for ((address, name) in components) {
          if (filter?.isEmpty() != false || matches(name)) {
            table.set(address, name)
          }
        }
        table
      }
    }

    component.setClosure("type") { args ->
      synchronized(components) {
        when (val name = components[args.checkjstring(1)]) {
          is String -> LuaValue.valueOf(name)
          else -> luaError("no such component")
        }
      }
    }

    component.setClosure("slot") { args ->
      synchronized(components) {
        when (val address = components[args.checkjstring(1)]) {
          is String -> LuaValue.valueOf(machine.host().componentSlot(address))
          else -> luaError("no such component")
        }
      }
    }

    component.setClosure("methods") { args ->
      withComponent(args.checkjstring(1)) { component ->
        val table = LuaValue.tableOf()
        for ((name, annotation) in machine.methods(component.host())) {
          table.set(name, LuaValue.tableOf(arrayOf(
            LuaValue.valueOf("direct"),
            LuaValue.valueOf(annotation.direct),
            LuaValue.valueOf("getter"),
            LuaValue.valueOf(annotation.getter),
            LuaValue.valueOf("setter"),
            LuaValue.valueOf(annotation.setter))))
        }
        table
      }
    }

    component.setClosure("invoke") { args ->
      val address = args.checkjstring(1)
      val method = args.checkjstring(2)
      val params = toSimpleJavaObjects(args, 3)
      owner.invoke { machine.invoke(address, method, params.toTypedArray()) }
    }

    component.setClosure("doc") { args ->
      withComponent(args.checkjstring(1)) { component ->
        val method = args.checkjstring(2)
        val methods = machine.methods(component.host())
        owner.documentation { methods[method]?.doc }
      }
    }

    lua.set("component", component)
  }

  private fun withComponent(address: String, f: (Component) -> Varargs): Varargs {
    val component = node.network().node(address)
    if (component is Component && (component.canBeSeenFrom(node) || component == node)) {
      return f(component)
    }
    return luaError("no such component")
  }
}
