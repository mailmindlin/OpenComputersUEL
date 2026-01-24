package li.cil.oc.server.machine.luac

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

import li.cil.oc.OpenComputers
import li.cil.oc.api.Persistable
import li.cil.oc.api.machine.Value
import li.cil.oc.server.driver.Registry
import li.cil.oc.server.machine.ArgumentsImpl
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound

class UserdataAPI(owner: NativeLuaArchitecture): NativeLuaAPI(owner) {
  override fun initialize() {
    lua.newTable()

    lua.pushClosure { lua ->
      val nbt = NBTTagCompound()
      val persistable = lua.toJavaObjectRaw(1) as Persistable
      lua.pushString(persistable.javaClass.name)
      persistable.save(nbt)
      val baos = ByteArrayOutputStream()
      val dos = DataOutputStream(baos)
      CompressedStreamTools.write(nbt, dos)
      lua.pushByteArray(baos.toByteArray())
      2
    }
    lua.setField(-2, "save")

    lua.pushClosure { lua ->
      try {
        val className = lua.toString(1)
        val clazz = Class.forName(className)
        val persistable = clazz.newInstance() as Persistable
        val data = lua.toByteArray(2)
        val bais = ByteArrayInputStream(data)
        val dis = DataInputStream(bais)
        val nbt = CompressedStreamTools.read(dis)
        persistable.load(nbt)
        lua.pushJavaObjectRaw(persistable)
        1
      } catch (t: Throwable) {
        OpenComputers.log.warn("Error in userdata load function.", t)
        throw t
      }
    }
    lua.setField(-2, "load")

    lua.pushClosure { lua ->
      val value = lua.toJavaObjectRaw(1) as Value
      val args = lua.toSimpleJavaObjects(2)
      owner.invoke { Registry.run { arrayOf(value.apply(machine, ArgumentsImpl(args))).convert() } }
    }
    lua.setField(-2, "apply")

    lua.pushClosure { lua ->
      val value = lua.toJavaObjectRaw(1) as Value
      val args = lua.toSimpleJavaObjects(2)
      owner.invoke {
        value.unapply(machine, ArgumentsImpl(args))
        null
      }
    }
    lua.setField(-2, "unapply")

    lua.pushClosure { lua ->
      val value = lua.toJavaObjectRaw(1) as Value
      val args = lua.toSimpleJavaObjects(2)
      owner.invoke { Registry.run { value.call(machine, ArgumentsImpl(args)).convert() } }
    }
    lua.setField(-2, "call")

    lua.pushClosure { lua ->
      val value = lua.toJavaObjectRaw(1) as Value
      try {
        value.dispose(machine)
      } catch (t: Exception) {
        OpenComputers.log.warn("Error in dispose method of userdata of type " + value.javaClass.name, t)
      }
      0
    }
    lua.setField(-2, "dispose")

    lua.pushClosure { lua ->
      val value = lua.toJavaObjectRaw(1) as Value
      lua.pushValue(machine.methods(value).mapValues { (_, annotation) -> annotation.direct })
      1
    }
    lua.setField(-2, "methods")

    lua.pushClosure { lua ->
      val value = lua.toJavaObjectRaw(1) as Value
      val method = lua.checkString(2)
      val args = lua.toSimpleJavaObjects(3)
      owner.invoke { machine.invoke(value, method, args.toTypedArray()) }
    }
    lua.setField(-2, "invoke")

    lua.pushClosure { lua ->
      val value = lua.toJavaObjectRaw(1) as Value
      val method = lua.checkString(2)
      owner.documentation { machine.methods(value)?.get(method)?.doc }
    }
    lua.setField(-2, "doc")

    lua.setGlobal("userdata")
  }
}
