package li.cil.oc.server.machine.luaj

import li.cil.oc.OpenComputers
import li.cil.oc.api.machine.Value
import li.cil.oc.server.driver.Registry
import li.cil.oc.server.machine.ArgumentsImpl
import li.cil.oc.util.LuaClosure.Companion.toSimpleJavaObjects
import li.cil.repack.org.luaj.vm2.LuaValue
import li.cil.repack.org.luaj.vm2.Varargs

private fun Varargs.checkValue(i: Int = 1): Value = checkuserdata(i, Value::class.java) as Value

internal sealed class UserdataAPI(owner: LuaJLuaArchitecture): LuaJAPI(owner) {
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
      try value.dispose(machine) catch {
        case t: Throwable => OpenComputers.log.warn("Error in dispose method of userdata of type " + value.getClass.getName, t)
      }
      LuaValue.NIL
    }

    userdata.setClosure("methods") { args ->
      val value = args.checkValue(1)
      LuaValue.tableOf(machine.methods(value).map(entry => {
        val (name, annotation) = entry
        Seq(LuaValue.valueOf(name), LuaValue.valueOf(annotation.direct))
      }).flatten.toArray)
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
