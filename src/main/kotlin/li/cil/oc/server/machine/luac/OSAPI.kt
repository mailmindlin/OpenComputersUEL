package li.cil.oc.server.machine.luac

import li.cil.oc.server.machine.OsApi
import li.cil.oc.util.GameTimeFormatter
import li.cil.repack.com.naef.jnlua.LuaType

class OSAPI(owner: NativeLuaArchitecture): NativeLuaAPI(owner) {
  override fun initialize() {
    // Push a couple of functions that override original Lua API functions or
    // that add new functionality to it.
    lua.getGlobal("os")

    // Custom os.clock() implementation returning the time the computer has
    // been actively running, instead of the native library...
    lua.pushJavaFunction { lua ->
      lua.pushNumber(machine.cpuTime())
      1
    }
    lua.setField(-2, "clock")

    // Date formatting function.
    lua.pushJavaFunction { lua ->
      val format =
        if (lua.top > 0 && lua.isString(1)) lua.toString(1)
        else "%d/%m/%y %H:%M:%S"
      val time =
        if (lua.top > 1 && lua.isNumber(2)) lua.toNumber(2)
        else ((machine.worldTime() + 6000) * 60 * 60) / 1000.0

      val dt = GameTimeFormatter.parse(time)
      fun fmt(format: String) {
        if (format == "*t") {
          lua.newTable(0, 8)
          lua.pushInteger(dt.year.toLong())
          lua.setField(-2, "year")
          lua.pushInteger(dt.month.toLong())
          lua.setField(-2, "month")
          lua.pushInteger(dt.day.toLong())
          lua.setField(-2, "day")
          lua.pushInteger(dt.hour.toLong())
          lua.setField(-2, "hour")
          lua.pushInteger(dt.minute.toLong())
          lua.setField(-2, "min")
          lua.pushInteger(dt.second.toLong())
          lua.setField(-2, "sec")
          lua.pushInteger(dt.weekDay.toLong())
          lua.setField(-2, "wday")
          lua.pushInteger(dt.yearDay.toLong())
          lua.setField(-2, "yday")
        } else {
          lua.pushString(GameTimeFormatter.format(format, dt))
        }
      }

      // Just ignore the allowed leading '!', Minecraft has no time zones...
      if (format.startsWith("!"))
        fmt(format.substring(1))
      else
        fmt(format)
      1
    }
    lua.setField(-2, "date")

    // Return ingame time for os.time().
    lua.pushJavaFunction { lua ->
      if (lua.isNoneOrNil(1)) {
        // Game time is in ticks, so that each day has 24000 ticks, meaning
        // one hour is game time divided by one thousand. Also, Minecraft
        // starts days at 6 o'clock; os.time() reflects UTC while os.date()
        // reflects the local time zone, but Minecraft has no concept of
        // time zones, so this detail can be ignored. Thus:
        // timestamp = (time + 6000) * 60[kh] * 60[km] / 1000[s]
        lua.pushNumber(OsApi.time(machine))
      } else {
        fun getField(key: String, d: Int): Int {
          lua.getField(-1, key)
          val res = lua.toIntegerX(-1)
          lua.pop(1)
          return if (res == null)
            if (d < 0) throw Exception("field '$key' missing in date table")
            else d
          else res.toInt()
        }

        lua.checkType(1, LuaType.TABLE)
        lua.top = 1

        val sec = getField("sec", 0)
        val min = getField("min", 0)
        val hour = getField("hour", 12)
        val mday = getField("day", -1)
        val mon = getField("month", -1)
        val year = getField("year", -1)

        GameTimeFormatter.mktime(year, mon, mday, hour, min, sec)
          ?.let { lua.pushInteger(it.toLong()) }
          ?: lua.pushNil()
      }
      1
    }
    lua.setField(-2, "time")

    // Pop the os table.
    lua.pop(1)
  }
}
