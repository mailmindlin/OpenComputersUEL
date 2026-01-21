package li.cil.oc.server.machine

import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.item.MutableProcessor
import li.cil.oc.api.driver.item.Processor
import li.cil.oc.api.machine.Architecture
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.server.machine.luaj.luaError
import li.cil.oc.server.machine.luaj.setClosure
import li.cil.oc.util.ExtendedUnicodeHelper
import li.cil.oc.util.FontUtils
import li.cil.repack.org.luaj.vm2.LuaValue
import java.util.function.IntUnaryOperator
import kotlin.math.max
import kotlin.math.min

object OsApi {
    /**
     * Game time is in ticks, so that each day has 24000 ticks, meaning
     * one hour is game time divided by one thousand. Also, Minecraft
     * starts days at 6 o'clock; os.time() reflects UTC while os.date()
     * reflects the local time zone, but Minecraft has no concept of
     * time zones, so this detail can be ignored. Thus:
     * timestamp = (time + 6000) * 60[kh] * 60[km] / 1000[s]
     */
    internal fun time(machine: Machine): Double = (machine.worldTime() + 6000) * 60 * 60 / 1000.0
}

internal class UnknownArchitectureException: Exception() {}

object ComputerApi {
    fun ArchitectureAPI.energy(): Double {
        return if (Settings.get.ignorePower)
            Double.POSITIVE_INFINITY
        else
            (machine.node() as Connector).globalBuffer()
    }

    fun ArchitectureAPI.maxEnergy(): Double = (machine.node() as Connector).globalBufferSize()

    fun ArchitectureAPI.getArchitectures(): Sequence<String> {
        return machine.host().internalComponents()
            .firstNotNullOfOrNull { stack ->
                when (val processor = Driver.driverFor(stack)) {
                    is MutableProcessor -> processor.allArchitectures()
                    is Processor -> listOf(processor.architecture(stack))
                    else -> return@firstNotNullOfOrNull null
                }
            }
            ?.let { archs ->
                archs.asSequence().map { li.cil.oc.api.Machine.getArchitectureName(it) }
            }
            ?: emptySequence<String>()
    }

    fun ArchitectureAPI.getArchitecture(): String? {
        return machine.host().internalComponents()
            .firstNotNullOfOrNull { stack ->
                val processor = Driver.driverFor(stack) as? Processor ?: return@firstNotNullOfOrNull null
                li.cil.oc.api.Machine.getArchitectureName(processor.architecture(stack))
            }
    }

    fun ArchitectureAPI.setArchitecture(archName: String): Boolean? {
        return machine
            .host()
            .internalComponents()
            .firstNotNullOfOrNull { stack ->
                val processor = Driver.driverFor(stack) as? MutableProcessor ?: return@firstNotNullOfOrNull null
                val architecture = processor.allArchitectures()
                    .find { arch -> li.cil.oc.api.Machine.getArchitectureName(arch) == archName }
                    ?: throw UnknownArchitectureException()

                if (architecture != processor.architecture(stack)) {
                    processor.setArchitecture(stack, architecture)
                    true
                } else {
                    false
                }
            }
    }
}

object UnicodeApi {
    fun char(narg: Int, checkint: (Int) -> Int): String {
        val builder = StringBuilder()
        for (i in 1 until narg) {
            builder.appendCodePoint(checkint(i))
        }
        return builder.toString()
    }
    fun sub(string: String, i: Int, j: Int?): String {
        val sLength = ExtendedUnicodeHelper.length(string)
        val start = when {
            i < 0 -> string.offsetByCodePoints(string.length, max(i, -sLength))
            i == 0 -> 0
            else -> string.offsetByCodePoints(0, min(i - 1, sLength))
        }
        val end = when {
            j == null -> string.length
            j < 0 -> string.offsetByCodePoints(string.length, max(i + 1, -sLength))
            else  -> string.offsetByCodePoints(0, min(i, sLength))
        }

        return if (end <= start) ""
        else string.substring(start, end)
    }

    fun isWide(value: String): Boolean = FontUtils.wcwidth(value.codePointAt(0)) > 1
    fun charWidth(value: String): Int = FontUtils.wcwidth(value.codePointAt(0))
    fun wlen(value: String): Int {
        return value.codePoints().map(object : IntUnaryOperator {
            override fun applyAsInt(ch: Int): Int = FontUtils.wcwidth(ch).coerceAtLeast(1)
        }).sum()
    }

    fun wtrunc(value: String, count: Int): String {
        var width = 0
        var end = 0
        while (width < count) {
            width += FontUtils.wcwidth(value.codePointAt(end)).coerceAtLeast(1)
            end = value.offsetByCodePoints(end, 1)
        }
        return if (end > 1) value.substring(0, end - 1)
        else ""
    }

}