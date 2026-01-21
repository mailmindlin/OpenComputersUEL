package li.cil.oc.server.machine.luaj

import li.cil.oc.server.machine.UnicodeApi
import li.cil.oc.util.ExtendedUnicodeHelper
import li.cil.repack.org.luaj.vm2.LuaValue

/** Provide some better Unicode support. */
class UnicodeAPI(owner: LuaJLuaArchitecture): LuaJAPI(owner) {
  override fun initialize() {
    val unicode = LuaValue.tableOf()

    unicode.setClosure("lower") { args -> LuaValue.valueOf(args.checkjstring(1).lowercase()) }

    unicode.setClosure("upper") { args -> LuaValue.valueOf(args.checkjstring(1).uppercase()) }

    unicode.setClosure("char") { args -> LuaValue.valueOf(UnicodeApi.char(args.narg(), args::checkint)) }

    unicode.setClosure("len") { args ->
      val s = args.checkjstring(1)
      LuaValue.valueOf(s.codePointCount(0, s.length))
    }

    unicode.setClosure("reverse") { args -> LuaValue.valueOf(ExtendedUnicodeHelper.reverse(args.checkjstring(1))) }

    unicode.setClosure("sub") { args ->
      val string = args.checkjstring(1)
      val i = args.checkint(2)
      val j = if (args.narg() > 2) args.checkint(3) else null
      LuaValue.valueOf(UnicodeApi.sub(string, i, j))
    }

    unicode.setClosure("isWide") { args -> LuaValue.valueOf(UnicodeApi.isWide(args.checkjstring(1))) }

    unicode.setClosure("charWidth") { args -> LuaValue.valueOf(UnicodeApi.charWidth(args.checkjstring(1))) }

    unicode.setClosure("wlen") { args ->
      val value = args.checkjstring(1)
      LuaValue.valueOf(UnicodeApi.wlen(value))
    }

    unicode.setClosure("wtrunc") { args ->
      val value = args.checkjstring(1)
      val count = args.checkint(2)
      LuaValue.valueOf(UnicodeApi.wtrunc(value, count))
    }

    lua.set("unicode", unicode)
  }
}
