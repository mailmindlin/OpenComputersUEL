package li.cil.oc.util

import com.google.common.net.InetAddresses
import java.net.InetAddress
import kotlin.jvm.Throws

// Originally by SquidDev
internal class InetAddressRange private constructor(private val min: ByteArray, private val max: ByteArray) {
    fun matches(address: InetAddress): Boolean {
        val entry = address.address
        if (entry.size != min.size) return false

        for (i in entry.indices) {
            val value = 0xFF and entry[i].toInt()
            if (value < (0xFF and min[i].toInt()) || value > (0xFF and max[i].toInt())) return false
        }

        return true
    }

    companion object {
        @JvmStatic
        @Throws(IllegalArgumentException::class)
        fun parse(addressStr: String, prefixSizeStr: String): InetAddressRange {
            val prefixSize = try {
                prefixSizeStr.toUInt().toInt()
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Malformed address range entry '$addressStr/$prefixSizeStr': Cannot extract size of CIDR mask from '$prefixSizeStr'.", e)
            }

            val address = try {
                InetAddresses.forString(addressStr)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Malformed address range entry '$addressStr/$prefixSizeStr': Cannot extract IP address from '$addressStr'.", e)
            }

            // Mask the bytes of the IP address.
            val minBytes = address.address
            val maxBytes = address.address
            var size = prefixSize
            for (i in minBytes.indices) {
                if (size <= 0) {
                    minBytes[i] = 0.toByte()
                    maxBytes[i] = 0xFF.toByte()
                } else if (size < 8) {
                    minBytes[i] = (minBytes[i].toInt() and (0xFF shl (8 - size))).toByte()
                    maxBytes[i] = (maxBytes[i].toInt() or (0xFF shl (8 - size)).inv()).toByte()
                }

                size -= 8
            }

            return InetAddressRange(minBytes, maxBytes)
        }
    }
}