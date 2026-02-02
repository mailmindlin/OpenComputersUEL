package li.cil.oc.util

internal val String.unicodeLength: Int
    get() = codePointCount(0, this.length)
internal fun String.unicodeReversed(): String = ExtendedUnicodeHelper.reverse(this)

/**
 * Helper functions for handling strings with characters outside of the Unicode BMP.
 */
internal object ExtendedUnicodeHelper {
    fun reverse(s: String): String {
        val sb = StringBuilder()
        var i = s.length - 1
        while (i >= 0) {
            val c = s[i]
            if (Character.isLowSurrogate(c) && i > 0) {
                i--
                val c2 = s[i]
                if (Character.isHighSurrogate(c2)) {
                    sb.append(c2).append(c)
                } else {
                    // Invalid surrogate pair?
                    sb.append(c).append(c2)
                }
            } else {
                sb.append(c)
            }
            i--
        }
        return sb.toString()
    }

    fun substring(s: String, start: Int, end: Int): String {
        return s.substring(
            s.offsetByCodePoints(0, start),
            s.offsetByCodePoints(0, end)
        )
    }
}
