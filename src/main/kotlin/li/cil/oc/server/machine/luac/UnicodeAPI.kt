package li.cil.oc.server.machine.luac

import li.cil.oc.server.machine.UnicodeApi
import li.cil.oc.util.ExtendedUnicodeHelper
import li.cil.oc.util.unicodeLength
import li.cil.oc.util.unicodeReversed

/** Provide some better Unicode support. */
class UnicodeAPI(owner: NativeLuaArchitecture): NativeLuaAPI(owner) {
  override fun initialize() {

    lua.newTable()

    lua.pushJavaFunction { lua ->
      lua.pushString(lua.checkString(1).uppercase())
      1
    }
    lua.setField(-2, "upper")

    lua.pushJavaFunction { lua ->
      lua.pushString(lua.checkString(1).lowercase())
      1
    }
    lua.setField(-2, "lower")

    lua.pushJavaFunction { lua ->
      lua.pushString(UnicodeApi.char(lua.top, lua::checkInt32))
      1
    }
    lua.setField(-2, "char")

    lua.pushJavaFunction { lua ->
      val s = lua.checkString(1)
      lua.pushInteger(s.unicodeLength.toLong())
      1
    }
    lua.setField(-2, "len")

    lua.pushJavaFunction { lua ->
      lua.pushString(lua.checkString(1).unicodeReversed())
      1
    }
    lua.setField(-2, "reverse")

    lua.pushJavaFunction { lua ->
      val string = lua.checkString(1)
      val i = lua.checkInt32(2)
      val j = if (lua.top > 2) lua.checkInt32(3) else null
      lua.pushString(UnicodeApi.sub(string, i, j))
      1
    }
    lua.setField(-2, "sub")

    lua.pushJavaFunction { lua ->
      lua.pushBoolean(UnicodeApi.isWide(lua.checkString(1)))
      1
    }
    lua.setField(-2, "isWide")

    lua.pushJavaFunction { lua ->
      lua.pushInteger(UnicodeApi.charWidth(lua.checkString(1)).toLong())
      1
    }
    lua.setField(-2, "charWidth")

    lua.pushJavaFunction { lua ->
      val value = lua.checkString(1)
      lua.pushInteger(UnicodeApi.wlen(value).toLong())
      1
    }
    lua.setField(-2, "wlen")

    lua.pushJavaFunction { lua ->
      val value = lua.checkString(1)
      val count = lua.checkInteger(2)
      lua.pushString(UnicodeApi.wtrunc(value, count.toInt()))
      1
    }
    lua.setField(-2, "wtrunc")

    lua.setGlobal("unicode")
  }
}
