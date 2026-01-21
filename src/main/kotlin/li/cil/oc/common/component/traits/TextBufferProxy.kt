package li.cil.oc.common.component.traits

import li.cil.oc.util.TextBuffer as UtilTextBuffer
import li.cil.oc.api.internal.TextBuffer
import li.cil.oc.util.ExtendedUnicodeHelper
import li.cil.oc.util.PackedColor

interface TextBufferProxy: TextBuffer {
    val data: UtilTextBuffer

    override fun getWidth(): Int = data.width

    override fun getHeight(): Int = data.height

    override fun setColorDepth(depth: TextBuffer.ColorDepth): Boolean {
        if (depth.ordinal > maximumColorDepth.ordinal)
            throw IllegalArgumentException("unsupported depth")
        data.format = PackedColor.Depth.format(depth)
        return true
    }

    override fun getColorDepth(): TextBuffer.ColorDepth = data.format.depth

    fun onBufferPaletteChange(index: Int) {}

    override fun setPaletteColor(index: Int, color: Int) {
        val format = data.format
        if (format is PackedColor.MutablePaletteFormat) {
            format.set(index.toUInt(), color.toUInt())
            onBufferPaletteChange(index)
        } else {
            throw Exception("palette not available")
        }
    }

    override fun getPaletteColor(index: Int): Int {
        val format = data.format
        if (format is PackedColor.MutablePaletteFormat) {
            return format[index.toUInt()].toInt()
        } else {
            throw Exception("palette not available")
        }
    }

    fun onBufferColorChange() {}

    override fun setForegroundColor(color: Int) = setForegroundColor(color, false)

    override fun setForegroundColor(color: Int, isFromPalette: Boolean) {
        val value = PackedColor.Color(color.toUInt(), isFromPalette)
        if (data.foreground != value) {
            data.foreground = value
            onBufferColorChange()
        }
    }

    override fun getForegroundColor(): Int = data.foreground.value.toInt()

    override fun isForegroundFromPalette(): Boolean = data.foreground.isPalette

    override fun setBackgroundColor(color: Int) = setBackgroundColor(color, false)

    override fun setBackgroundColor(color: Int, isFromPalette: Boolean) {
        val value = PackedColor.Color(color, isFromPalette)
        if (data.background != value) {
            data.background = value
            onBufferColorChange()
        }
    }

    override fun getBackgroundColor(): Int = data.background.value.toInt()

    override fun isBackgroundFromPalette(): Boolean = data.background.isPalette

    fun onBufferCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int) {}

    override fun copy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int) {
        if (data.copy(col, row, w, h, tx, ty))
            onBufferCopy(col, row, w, h, tx, ty)
    }

    fun onBufferFill(col: Int, row: Int, w: Int, h: Int, c: Int) {}

    override fun fill(col: Int, row: Int, w: Int, h: Int, c: Char) =
        fill(col, row, w, h, c.code)

    override fun fill(col: Int, row: Int, w: Int, h: Int, c: Int) {
        if (data.fill(col, row, w, h, c))
            onBufferFill(col, row, w, h, c)
    }

    fun onBufferSet(col: Int, row: Int, s: String, vertical: Boolean) {}

    private fun truncate(s: String, sLength: Int, leftOffset: Int, maxWidth: Int): String {
        val subFrom = s.offsetByCodePoints(0, leftOffset)
        val width = minOf(sLength, maxWidth)
        return when {
            width <= 0 -> ""
            (sLength - leftOffset) <= width -> s
            else -> s.substring(subFrom, s.offsetByCodePoints(subFrom, width))
        }
    }

    override fun set(col: Int, row: Int, s: String, vertical: Boolean) {
        val sLength = ExtendedUnicodeHelper.length(s)
        if (col < data.width && (col >= 0 || -col < sLength)) {
            // Make sure the string isn't longer than it needs to be, in particular to
            // avoid sending too much data to our clients.
            val (x, y, truncated) = if (vertical) {
                if (row < 0) Triple(col, 0, truncate(s, sLength, -row, data.height))
                else Triple(col, row, truncate(s, sLength, 0, data.height - row))
            } else {
                if (col < 0) Triple(0, row, truncate(s, sLength, -col, data.width))
                else Triple(col, row, truncate(s, sLength, 0, data.width - col))
            }
            if (data.set(x, y, truncated, vertical))
                onBufferSet(x, row, truncated, vertical)
        }
    }

    override fun get(col: Int, row: Int): Char = data.get(col, row).toChar()

    override fun getCodePoint(col: Int, row: Int): Int = data.get(col, row)

    override fun getForegroundColor(column: Int, row: Int): Int =
        if (isForegroundFromPalette(column, row)) {
            PackedColor.extractForeground(color(column, row))
        } else {
            PackedColor.unpackForeground(color(column, row), data.format)
        }.toInt()

    override fun isForegroundFromPalette(column: Int, row: Int): Boolean =
        data.format.isFromPalette(PackedColor.extractForeground(color(column, row)))

    override fun getBackgroundColor(column: Int, row: Int): Int =
        if (isBackgroundFromPalette(column, row)) {
            PackedColor.extractBackground(color(column, row))
        } else {
            PackedColor.unpackBackground(color(column, row), data.format)
        }.toInt()

    override fun isBackgroundFromPalette(column: Int, row: Int): Boolean =
        data.format.isFromPalette(PackedColor.extractBackground(color(column, row)))

    override fun rawSetText(col: Int, row: Int, text: Array<CharArray>) {
        for (y in row until minOf(row + text.size, data.height)) {
            val line = text[y - row]
            System.arraycopy(line, 0, data.buffer[y], col, minOf(line.size, data.width))
        }
    }

    override fun rawSetText(col: Int, row: Int, text: Array<IntArray>) {
        for (y in row until minOf(row + text.size, data.height)) {
            val line = text[y - row]
            System.arraycopy(line, 0, data.buffer[y], col, minOf(line.size, data.width))
        }
    }

    override fun rawSetForeground(col: Int, row: Int, color: Array<IntArray>) {
        for (y in row until minOf(row + color.size, data.height)) {
            val line = color[y - row]
            for (x in col until minOf(col + line.size, data.width)) {
                val packedBackground = data.color[y][x].toInt() and 0x00FF
                val packedForeground = (data.format.deflate(PackedColor.Color(line[x - col])) shl PackedColor.ForegroundShift) and 0xFF00
                data.color[y][x] = (packedForeground or packedBackground).toShort()
            }
        }
    }

    override fun rawSetBackground(col: Int, row: Int, color: Array<IntArray>) {
        for (y in row until minOf(row + color.size, data.height)) {
            val line = color[y - row]
            for (x in col until minOf(col + line.size, data.width)) {
                val packedBackground = data.format.deflate(PackedColor.Color(line[x - col].toUInt())).toUInt() and 0x00FFu
                val packedForeground = data.color[y][x].toUInt() and 0xFF00u
                data.color[y][x] = (packedForeground or packedBackground).toShort()
            }
        }
    }

    private fun color(column: Int, row: Int): Short {
        if (column < 0 || column >= width || row < 0 || row >= height)
            throw IndexOutOfBoundsException()
        return data.color[row][column]
    }
}
