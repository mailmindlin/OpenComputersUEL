package li.cil.oc.client.renderer.font

import gnu.trove.map.TIntObjectMap
import gnu.trove.map.hash.TIntObjectHashMap
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.util.FontUtils
import li.cil.oc.util.FontUtils.wcwidth
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.IResource
import net.minecraft.util.ResourceLocation
import org.lwjgl.BufferUtils
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.jvm.Throws

private inline fun <T: Closeable> T.useLog(f: (T) -> Unit) {
    try {
        f(this)
    } finally {
        try {
            this.close()
        } catch (ex: IOException) {
            OpenComputers.log.warn("Error parsing font.", ex)
        }
    }
}

private inline fun Byte.forEachBit(f: (Boolean) -> Unit) {
    var c = this.toInt() and 0xFF
    // Grab all bits by grabbing the leftmost one then shifting.
    for (j in 0 until 8) {
        f((c and 0x80) != 0)
        c = c shl 1
    }
}

private inline fun ByteArray.forEachBit(f: (Boolean) -> Unit) {
    for (elt in this) {
        var c = elt.toInt() and 0xFF
        // Grab all bits by grabbing the leftmost one then shifting.
        for (j in 0 until 8) {
            f((c and 0x80) != 0)
            c = c shl 1
        }
    }
}

class FontParserHex : IGlyphProvider {
    private val glyphs: TIntObjectMap<ByteArray> = TIntObjectHashMap()

    override fun initialize() {
        try {
            glyphs.clear()

            OpenComputers.log.info("Loading Unicode glyphs...")
            val time = System.currentTimeMillis()
            var glyphCount = 0

            val loc = ResourceLocation(Settings.resourceDomain, "font.hex")
            for (resource in Minecraft.getMinecraft().resourceManager.getAllResources(loc) as List<IResource>) {
                resource.inputStream.useLog { font ->
                    for (line in font.bufferedReader().lineSequence()) {
                        val info = line.substring(0, line.indexOf(':'))
                        val charCode = info.toInt(16)
                        if (charCode < 0 || charCode >= FontUtils.CODEPOINT_LIMIT) {
                            OpenComputers.log.warn("Unicode font contained unexpected glyph: ${codePoint(charCode)}, ignoring")
                            continue  // Out of bounds.
                        }
                        val expectedWidth = wcwidth(charCode)
                        if (expectedWidth < 1) continue  // Skip control characters.

                        // Two chars representing one byte represent one row of eight pixels.
                        var glyphStrOfs = info.length + 1
                        val glyph = ByteArray((line.length - glyphStrOfs) shr 1)
                        val glyphWidth = glyph.size / glyphHeight
                        if (expectedWidth != glyphWidth) {
                            if (Settings.get.logHexFontErrors)
                                OpenComputers.log.warn("Size of glyph for code point ${codePoint(charCode)} (${charCode.toChar()}) in font ($glyphWidth) does not match expected width ($expectedWidth), ignoring.")
                            continue
                        }

                        for (i in glyph.indices) {
                            glyph[i] = ((hex2int(line[glyphStrOfs]) shl 4) or (hex2int(
                                line[glyphStrOfs + 1]
                            ))).toByte()
                            glyphStrOfs += 2
                        }
                        if (glyphs.put(charCode, glyph) == null)
                            // New value
                            glyphCount++
                    }
                }
            }

            OpenComputers.log.info("Loaded $glyphCount glyphs in ${System.currentTimeMillis() - time} milliseconds.")
        } catch (ex: IOException) {
            OpenComputers.log.warn("Failed loading glyphs.", ex)
        }
    }

    override fun getGlyph(charCode: Int): ByteBuffer? {
        val glyph = glyphs[charCode]?.takeIf { it.isNotEmpty() } ?: return null

        val buffer = BufferUtils.createByteBuffer(glyph.size * glyphWidth * 4)
        for (aGlyph in glyph) {
            aGlyph.forEachBit { isBitSet ->
                buffer.put(if (isBitSet) OPAQUE else TRANSPARENT)
            }
        }
        buffer.rewind()
        return buffer
    }

    override val glyphHeight: Int
        get() = 8
    override val glyphWidth: Int
        get() = 16

    companion object {
        private val OPAQUE = byteArrayOf(255.toByte(), 255.toByte(), 255.toByte(), 255.toByte())
        private val TRANSPARENT = byteArrayOf(0, 0, 0, 0)

        private operator fun <V> TIntObjectMap<V>.contains(key: Int) = containsKey(key)

        /** Format char code as `U+XXXX` for logging */
        private fun codePoint(charCode: Int): String = String.format("U+%04X", charCode)

        @Throws(RuntimeException::class)
        private fun hex2int(c: Char): Int {
            val base = when {
                c >= '0' && c <= '9' -> '0'.code
                c >= 'A' && c <= 'F' -> 'A'.code - 10
                c >= 'a' && c <= 'f' -> 'a'.code - 10
                else -> throw RuntimeException("invalid char: $c")
            }
            return c.code - base
        }
    }
}
