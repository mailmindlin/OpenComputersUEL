package li.cil.oc.server.machine.luaj

import li.cil.oc.server.machine.OsApi
import li.cil.oc.util.GameTimeFormatter
import li.cil.repack.org.luaj.vm2.LuaValue

class OSAPI(owner: LuaJLuaArchitecture): LuaJAPI(owner) {
  override fun initialize() {
    val os = LuaValue.tableOf()

    os.setClosure("clock") { LuaValue.valueOf(machine.cpuTime()) }

    // Date formatting function.
    os.setClosure("date") { args ->
      val format =
        if (args.narg() > 0 && args.isstring(1)) args.tojstring(1)
        else "%d/%m/%y %H:%M:%S"
      val time =
        if (args.narg() > 1 && args.isnumber(2)) args.todouble(2)
        else (machine.worldTime() + 6000) * 60 * 60 / 1000.0

      val dt = GameTimeFormatter.parse(time)
      fun fmt(format: String): LuaValue {
        return if (format == "*t") {
          val table = LuaValue.tableOf(0, 8)
          table.set("year", LuaValue.valueOf(dt.year))
          table.set("month", LuaValue.valueOf(dt.month))
          table.set("day", LuaValue.valueOf(dt.day))
          table.set("hour", LuaValue.valueOf(dt.hour))
          table.set("min", LuaValue.valueOf(dt.minute))
          table.set("sec", LuaValue.valueOf(dt.second))
          table.set("wday", LuaValue.valueOf(dt.weekDay))
          table.set("yday", LuaValue.valueOf(dt.yearDay))
          table
        } else {
          LuaValue.valueOf(GameTimeFormatter.format(format, dt))
        }
      }

      // Just ignore the allowed leading '!', Minecraft has no time zones...
      if (format.startsWith("!"))
        fmt(format.substring(1))
      else
        fmt(format)
    }

    // Return ingame time for os.time().
    os.setClosure("time") { args ->
      if (args.isnoneornil(1)) {
        // Game time is in ticks, so that each day has 24000 ticks, meaning
        // one hour is game time divided by one thousand. Also, Minecraft
        // starts days at 6 o'clock; os.time() reflects UTC while os.date()
        // reflects the local time zone, but Minecraft has no concept of
        // time zones, so this detail can be ignored. Thus:
        // timestamp = (time + 6000) * 60[kh] * 60[km] / 1000[s]
        LuaValue.valueOf(OsApi.time(machine))
      } else {
        val table = args.checktable(1)

        fun getField(key: String, d: Int): Int {
          val res = table.get(key)
          if (!res.isint()) {
            if (d < 0) throw Exception("field '" + key + "' missing in date table")
            return d
          }
          return res.toint()
        }

        val sec = getField("sec", 0)
        val min = getField("min", 0)
        val hour = getField("hour", 12)
        val mday = getField("day", -1)
        val mon = getField("month", -1)
        val year = getField("year", -1)

        GameTimeFormatter.mktime(year, mon, mday, hour, min, sec)
          ?.let(LuaValue::valueOf)
          ?: LuaValue.NIL
      }
    }

    lua.set("os", os)
  }
}
