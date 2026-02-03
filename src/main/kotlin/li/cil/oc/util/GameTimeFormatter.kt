package li.cil.oc.util

import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone

object GameTimeFormatter {
    // Locale? What locale? Seriously though, since this would depend on the
    // server's locale I think it makes more sense to keep it English always.
    private val weekDays = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val shortWeekDays = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val months = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val shortMonths = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val amPm = arrayOf("AM", "PM")

    class DateTime(
        val year: Int,
        val month: Int,
        val day: Int,
        val weekDay: Int,
        val yearDay: Int,
        val hour: Int,
        val minute: Int,
        val second: Int
    )

    // See http://www.cplusplus.com/reference/ctime/strftime/
    private val specifiers: Map<Char, (DateTime) -> String> = mapOf(
        'a' to { t -> shortWeekDays[t.weekDay - 1] },
        'A' to { t -> weekDays[t.weekDay - 1] },
        'b' to { t -> shortMonths[t.month - 1] },
        'B' to { t -> months[t.month - 1] },
        'c' to { t -> format("%a %b %e %H:%M:%S %Y", t) },
        'C' to { t -> "%02d".format(t.year / 100) },
        'd' to { t -> "%02d".format(t.day) },
        'D' to { t -> format("%m/%d/%y", t) },
        'e' to { t -> "%2d".format(t.day) },
        'F' to { t -> format("%Y-%m-%d", t) },
        //'g' to { t -> "" },
        //'G' to { t -> "" },
        'h' to { t -> format("%b", t) },
        'H' to { t -> "%02d".format(t.hour) },
        'I' to { t -> "%02d".format((t.hour + 11) % 12 + 1) },
        'j' to { t -> "%03d".format(t.yearDay) },
        'm' to { t -> "%02d".format(t.month) },
        'M' to { t -> "%02d".format(t.minute) },
        'n' to { _ -> "\n" },
        'p' to { t -> amPm[if (t.hour < 12) 0 else 1] },
        'r' to { t -> format("%I:%M:%S %p", t) },
        'R' to { t -> format("%H:%M", t) },
        'S' to { t -> "%02d".format(t.second) },
        't' to { _ -> "\t" },
        'T' to { t -> format("%H:%M:%S", t) },
        //'u' to { t -> "" },
        //'U' to { t -> "" },
        //'V' to { t -> "" },
        'w' to { t -> "${t.weekDay - 1}" },
        //'W' to { t -> "" },
        'x' to { t -> format("%D", t) },
        'X' to { t -> format("%T", t) },
        'y' to { t -> "%02d".format(t.year % 100) },
        'Y' to { t -> "%04d".format(t.year) },
        //'z' to { t -> "" },
        //'Z' to { t -> "" },
        '%' to { _ -> "%" }
    )

    @JvmStatic
    fun parse(time: Double): DateTime {
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = (time * 1000).toLong()

        return DateTime(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
            calendar.get(Calendar.DAY_OF_WEEK),
            calendar.get(Calendar.DAY_OF_YEAR),
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND)
        )
    }

    @JvmStatic
    fun format(format: String, time: DateTime): String {
        val result = StringBuilder()
        val iterator = format.iterator()
        while (iterator.hasNext()) {
            when (val c = iterator.nextChar()) {
                '%' -> if (iterator.hasNext()) {
                    val specifier = specifiers[iterator.nextChar()]
                    if (specifier != null) {
                        result.append(specifier(time))
                    }
                }
                else -> result.append(c)
            }
        }
        return result.toString()
    }

    @JvmStatic
    fun mktime(year: Int, mon: Int, mday: Int, hour: Int, min: Int, sec: Int): Int {
        val calendar = GregorianCalendar(TimeZone.getTimeZone("UTC"))
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, mon - 1)
        calendar.set(Calendar.DAY_OF_MONTH, mday)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, min)
        calendar.set(Calendar.SECOND, sec)

        return (calendar.timeInMillis / 1000).toInt()
    }
}
