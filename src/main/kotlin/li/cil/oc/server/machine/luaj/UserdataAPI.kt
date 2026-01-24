package li.cil.oc.server.machine.luaj

import li.cil.oc.OpenComputers
import li.cil.oc.api.machine.Value
import li.cil.oc.server.driver.Registry
import li.cil.oc.server.machine.ArgumentsImpl
import li.cil.oc.server.machine.luaj.LuaClosure.Companion.toSimpleJavaObjects
import li.cil.repack.org.luaj.vm2.LuaValue
import li.cil.repack.org.luaj.vm2.Varargs

private fun Varargs.checkValue(i: Int = 1): Value = checkuserdata(i, Value::class.java) as Value

internal class UserdataAPI(owner: LuaJLuaArchitecture): LuaJAPI(owner) {
  override fun initialize() {
    val userdata = LuaValue.tableOf()

    userdata.setClosure("apply") { args ->
      val value = args.checkValue(1)
      val params = toSimpleJavaObjects(args, 2)
      owner.invoke { Registry.convert(arrayOf(value.apply(machine, ArgumentsImpl(params)))) }
    }

    userdata.setClosure("unapply") { args ->
      val value = args.checkValue(1)
      val params = toSimpleJavaObjects(args, 2)
      owner.invoke {
        value.unapply(machine, ArgumentsImpl(params))
        null
      }
    }

    userdata.setClosure("call") { args ->
      val value = args.checkValue(1)
      val params = toSimpleJavaObjects(args, 2)
      owner.invoke {
        Registry.convert(value.call(machine, ArgumentsImpl(params)))
      }
    }

    userdata.setClosure("dispose") { args ->
      val value = args.checkValue(1)
      try {
        value.dispose(machine)
      } catch (e: Exception) {
        OpenComputers.log.warn("Error in dispose method of userdata of type " + value.javaClass.name, e)
      }
      LuaValue.NIL
    }

    userdata.setClosure("methods") { args ->
      val value = args.checkValue(1)
      LuaValue.tableOf(machine.methods(value)
        .flatMap { (name, annotation) -> listOf(LuaValue.valueOf(name), LuaValue.valueOf(annotation.direct)) }
        .toTypedArray()
      )
    }

    userdata.setClosure("invoke") { args ->
      val value = args.checkValue(1)
      val method = args.checkjstring(2)
      val params = toSimpleJavaObjects(args, 3)
      owner.invoke {
        machine.invoke(value, method, params.toTypedArray())
      }
    }

    userdata.setClosure("doc") { args ->
      val value = args.checkValue(1)
      val method = args.checkjstring(2)
      owner.documentation {
        machine.methods(value)?.get(method)?.doc
      }
    }

    lua.set("userdata", userdata)
  }
}
