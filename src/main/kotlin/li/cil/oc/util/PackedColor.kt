package li.cil.oc.util

import li.cil.oc.Settings
import li.cil.oc.api.Persistable
import li.cil.oc.api.internal.TextBuffer
import net.minecraft.nbt.NBTTagCompound
import kotlin.math.min

@OptIn(ExperimentalUnsignedTypes::class)
object PackedColor {

    object Depth {
        @JvmStatic
        fun bits(depth: TextBuffer.ColorDepth): Int = when (depth) {
            TextBuffer.ColorDepth.OneBit -> 1
            TextBuffer.ColorDepth.FourBit -> 4
            TextBuffer.ColorDepth.EightBit -> 8
        }

        @JvmStatic
        fun format(depth: TextBuffer.ColorDepth): ColorFormat = when (depth) {
            TextBuffer.ColorDepth.OneBit -> SingleBitFormat
            TextBuffer.ColorDepth.FourBit -> MutablePaletteFormat()
            TextBuffer.ColorDepth.EightBit -> HybridFormat()
        }
    }

    private const val rShift32 = 16
    private const val gShift32 = 8
    private const val bShift32 = 0

    private fun extract(value: UInt): Triple<UByte, UByte, UByte> {
        val r = ((value shr rShift32) and 0xFFu).toUByte()
        val g = ((value shr gShift32) and 0xFFu).toUByte()
        val b = ((value shr bShift32) and 0xFFu).toUByte()
        return Triple(r, g, b)
    }

    interface ColorFormat : Persistable {
        val depth: TextBuffer.ColorDepth

        fun inflate(value: UInt): UInt

        fun validate(value: Color) {
            if (value.isPalette) {
                throw IllegalArgumentException("color palette not supported")
            }
        }

        fun deflate(value: Color): UByte

        fun isFromPalette(value: UInt): Boolean = false

        override fun load(nbt: NBTTagCompound) {}

        override fun save(nbt: NBTTagCompound) {}
    }

    open class SingleBitFormat(private val color: UInt) : ColorFormat {
        override val depth: TextBuffer.ColorDepth
            get() = TextBuffer.ColorDepth.OneBit

        override fun inflate(value: UInt): UInt = if (value == 0u) 0x000000u else color

        override fun deflate(value: Color): UByte {
            return if (value.value == 0u) 0u else 1u
        }

        companion object : SingleBitFormat(Settings.get.monochromeColor.toUInt())
    }

    abstract class PaletteFormat : ColorFormat {
        override fun inflate(value: UInt): UInt = palette[value.toInt().coerceIn(0..palette.size - 1)]

        override fun validate(value: Color) {
            if (value.isPalette && (value.value < 0u || value.value.toInt() >= palette.size)) {
                throw IllegalArgumentException("invalid palette index")
            }
        }

        override fun deflate(value: Color): UByte {
            return if (value.isPalette) {
                (value.value.toInt().coerceAtLeast(0) % palette.size).toUByte()
            } else {
                palette
                    .mapIndexed { index, color -> delta(value.value, color) to index }
                    .minBy { it.first }
                    .second
                    .toUByte()
            }
        }

        override fun isFromPalette(value: UInt): Boolean = true

        protected abstract val palette: UIntArray

        protected fun delta(colorA: UInt, colorB: UInt): Double {
            val (rA, gA, bA) = extract(colorA)
            val (rB, gB, bB) = extract(colorB)
            val dr = (rA - rB).toInt()
            val dg = (gA - gB).toInt()
            val db = (bA - bB).toInt()
            return 0.2126 * dr * dr + 0.7152 * dg * dg + 0.0722 * db * db
        }
    }

    open class MutablePaletteFormat : PaletteFormat() {
        override val depth: TextBuffer.ColorDepth
            get() = TextBuffer.ColorDepth.FourBit

        operator fun get(index: UInt): UInt = palette[index.toInt()]

        operator fun set(index: UInt, value: UInt) {
            palette[index.toInt()] = value
        }

        override val palette: UIntArray = uintArrayOf(
            0xFFFFFFu, 0xFFCC33u, 0xCC66CCu, 0x6699FFu,
            0xFFFF33u, 0x33CC33u, 0xFF6699u, 0x333333u,
            0xCCCCCCu, 0x336699u, 0x9933CCu, 0x333399u,
            0x663300u, 0x336600u, 0xFF3333u, 0x000000u
        )

        override fun load(nbt: NBTTagCompound) {
            val loaded = nbt.getIntArray("palette")
            System.arraycopy(loaded, 0, palette, 0, min(loaded.size, palette.size))
        }

        override fun save(nbt: NBTTagCompound) {
            nbt.setIntArray("palette", palette.asIntArray())
        }
    }

    class HybridFormat : MutablePaletteFormat() {
        private val reds = 6
        private val greens = 8
        private val blues = 5

        private val staticPalette = UIntArray(240)

        init {
            for (index in staticPalette.indices) {
                val idxB = index % blues
                val idxG = (index / blues) % greens
                val idxR = (index / blues / greens) % reds
                val r = (idxR * 0xFF / (reds - 1.0) + 0.5).toUInt()
                val g = (idxG * 0xFF / (greens - 1.0) + 0.5).toUInt()
                val b = (idxB * 0xFF / (blues - 1.0) + 0.5).toUInt()
                staticPalette[index] = (r shl rShift32) or (g shl gShift32) or (b shl bShift32)
            }

            // Initialize palette to grayscale, excluding black and white, because
            // those are already contained in the normal color cube.
            for (i in palette.indices) {
                val shade = (0xFF * (i + 1) / (palette.size + 1)).toUInt()
                this[i.toUInt()] = (shade shl rShift32) or (shade shl gShift32) or (shade shl bShift32)
            }
        }

        override val depth: TextBuffer.ColorDepth
            get() = TextBuffer.ColorDepth.EightBit

        override fun inflate(value: UInt): UInt {
            return if (isFromPalette(value)) super.inflate(value)
            else staticPalette[(value.toInt() - palette.size) % 240]
        }

        override fun deflate(value: Color): UByte {
            val paletteIndex = super.deflate(value)
            return if (value.isPalette) {
                paletteIndex
            } else {
                val (r, g, b) = extract(value.value)
                val idxR = (r.toDouble() * (reds.toDouble() - 1.0) / 0xFF + 0.5).toInt()
                val idxG = (g.toDouble() * (greens.toDouble() - 1.0) / 0xFF + 0.5).toInt()
                val idxB = (b.toDouble() * (blues.toDouble() - 1.0) / 0xFF + 0.5).toInt()
                val deflated = (palette.size + idxR * greens * blues + idxG * blues + idxB).toUByte()
                if (delta(inflate(deflated.toUInt()), value.value) < delta(inflate(paletteIndex.toUInt()), value.value)) {
                    deflated
                } else {
                    paletteIndex
                }
            }
        }

        override fun isFromPalette(value: UInt): Boolean = value >= 0u && value.toInt() < palette.size
    }

    data class Color(val value: UInt, val isPalette: Boolean = false)

    // Colors are packed: 0xFFBB (F = foreground, B = background)
    const val ForegroundShift = 8
    const val BackgroundMask = 0x000000FFu

    @JvmStatic
    fun pack(foreground: Color, background: Color, format: ColorFormat): Short {
        return (((format.deflate(foreground).toInt() and 0xFF) shl ForegroundShift) or (format.deflate(background).toInt() and 0xFF)).toShort()
    }

    @JvmStatic
    fun extractForeground(color: Short): UInt = (color.toInt().toUInt() and 0xFFFFu) shr ForegroundShift

    @JvmStatic
    fun extractBackground(color: Short): UInt = color.toInt().toUInt() and BackgroundMask

    @JvmStatic
    fun unpackForeground(color: Short, format: ColorFormat): UInt =
        format.inflate(extractForeground(color))

    @JvmStatic
    fun unpackBackground(color: Short, format: ColorFormat): UInt =
        format.inflate(extractBackground(color))
}
