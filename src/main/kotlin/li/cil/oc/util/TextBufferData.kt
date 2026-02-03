package li.cil.oc.util

import li.cil.oc.Settings
import li.cil.oc.api.internal.TextBuffer.ColorDepth
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.nbt.NBTTagString
import net.minecraftforge.common.util.Constants.NBT
import kotlin.math.max
import kotlin.math.min

/**
 * A 2D text buffer that stores characters and their associated colors, optimized for terminal-like displays.
 *
 * This class provides efficient storage and manipulation of a character grid with per-cell color information.
 * It's designed to minimize bandwidth costs by supporting bulk operations (copy, fill) rather than requiring
 * individual cell updates. The buffer handles:
 * - Wide characters (e.g., CJK characters that occupy 2 columns)
 * - Multiple color depth formats (1-bit, 4-bit, 8-bit)
 * - Efficient resizing with data preservation
 * - NBT serialization for persistence
 *
 * @property width The current width of the buffer in columns (minimum 1)
 * @property height The current height of the buffer in rows (minimum 1)
 * @param initialFormat The color format to use for color packing
 *
 * @see PackedColor for color encoding details
 * @see FontUtils.wcwidth for wide character handling
 */
internal class TextBufferData(var width: Int, var height: Int, initialFormat: PackedColor.ColorFormat) {
    constructor(size: ScreenResolution, format: PackedColor.ColorFormat) : this(size.width, size.height, format)

    private var _format: PackedColor.ColorFormat = initialFormat

    private var _foreground: PackedColor.Color = PackedColor.Color(0xFFFFFFu)

    private var _background: PackedColor.Color = PackedColor.Color(0x000000u)

    private var packed: UShort = PackedColor.pack(_foreground, _background, _format)

    var foreground: PackedColor.Color
        get() = _foreground
        set(value) {
            format.validate(value)
            _foreground = value
            packed = PackedColor.pack(_foreground, _background, _format)
        }

    var background: PackedColor.Color
        get() = _background
        set(value) {
            format.validate(value)
            _background = value
            packed = PackedColor.pack(_foreground, _background, _format)
        }

    var format: PackedColor.ColorFormat
        get() = _format
        set(value) {
            if (_format.depth != value.depth) {
                for (row in 0 until height) {
                    val rowColor = color[row]
                    for (col in 0 until width) {
                        val packed = rowColor[col].toUShort()
                        val fg = PackedColor.Color(PackedColor.unpackForeground(packed, _format))
                        val bg = PackedColor.Color(PackedColor.unpackBackground(packed, _format))
                        rowColor[col] = PackedColor.pack(fg, bg, value).toShort()
                    }
                }
                _format = value
                packed = PackedColor.pack(_foreground, _background, _format)
            }
        }

    var color: Array<ShortArray> = Array(height) { ShortArray(width) { packed.toShort() } }

    var buffer: Array<IntArray> = Array(height) { IntArray(width) { 0x20 } }

    /** The current buffer size in columns by rows. */
    val size: ScreenResolution get() = width by height

    /**
     * Set the new buffer size, returns true if the size changed.
     *
     * This will perform a proper resize as required, keeping as much of the
     * buffer valid as possible if the size decreases, i.e. only data outside the
     * new buffer size will be truncated, all data still inside will be copied.
     */
    fun setSize(value: ScreenResolution): Boolean {
        val (iw, ih) = value
        val w = max(iw, 1)
        val h = max(ih, 1)
        if (width != w || height != h) {
            val newBuffer = Array(h) { IntArray(w) { 0x20 } }
            val newColor = Array(h) { ShortArray(w) { packed.toShort() } }
            for (y in 0 until min(h, height)) {
                System.arraycopy(buffer[y], 0, newBuffer[y], 0, min(w, width))
                System.arraycopy(color[y], 0, newColor[y], 0, min(w, width))
            }
            buffer = newBuffer
            color = newColor
            width = w
            height = h
            return true
        }
        return false
    }

    /** Get the char at the specified index. */
    fun get(col: Int, row: Int): Int {
        if (col < 0 || col >= width || row < 0 || row >= height)
            throw IndexOutOfBoundsException()
        return buffer[row][col]
    }

    /** String based fill starting at a specified location. */
    fun set(col: Int, row: Int, s: String, vertical: Boolean): Boolean {
        val sLength = s.unicodeLength
        return if (vertical) {
            if (col < 0 || col >= width) false
            else {
                var changed = false
                var cx = 0
                for (y in row until min(row + sLength, height)) {
                    if (y >= 0) {
                        val line = buffer[y]
                        val lineColor = color[y]
                        val c = s.codePointAt(cx)
                        changed = changed || (line[col] != c) || (lineColor[col] != packed.toShort())
                        setChar(line, lineColor, col, c)
                        cx = s.offsetByCodePoints(cx, 1)
                    }
                }
                changed
            }
        } else {
            if (row < 0 || row >= height) false
            else {
                var changed = false
                val line = buffer[row]
                val lineColor = color[row]
                var bx = max(col, 0)
                var cx = 0
                for (x in bx until min(col + sLength, width)) {
                    if (bx < line.size) {
                        val c = s.codePointAt(cx)
                        changed = changed || (line[bx] != c) || (lineColor[bx] != packed.toShort())
                        setChar(line, lineColor, bx, c)
                        bx += max(1, FontUtils.wcwidth(c))
                        cx = s.offsetByCodePoints(cx, 1)
                    }
                }
                changed
            }
        }
    }

    /** Fills an area of the buffer with the specified character. */
    fun fill(col: Int, row: Int, w: Int, h: Int, c: Int): Boolean {
        // Anything to do at all?
        if (w <= 0 || h <= 0) return false
        if (col + w < 0 || row + h < 0 || col >= width || row >= height) return false
        var changed = false
        for (y in max(row, 0) until min(row + h, height)) {
            val line = buffer[y]
            val lineColor = color[y]
            var bx = max(col, 0)
            for (x in bx until min(col + w, width)) {
                if (bx < line.size) {
                    changed = changed || (line[bx] != c) || (lineColor[bx] != packed.toShort())
                    setChar(line, lineColor, bx, c)
                    bx += max(1, FontUtils.wcwidth(c))
                }
            }
        }
        return changed
    }

    /**
     * Copies a rectangular region to another location within the buffer.
     *
     * This handles overlapping regions correctly by choosing the appropriate iteration direction.
     * When copying a region that overlaps with its destination, the algorithm iterates in a
     * direction that ensures source data isn't overwritten before being copied.
     *
     * Wide characters at region boundaries are handled specially:
     * - If a wide character at the left edge of the destination would be split, it's cleared
     * - Copied wide characters have their second column filled with spaces
     *
     * @param col Source column (0-indexed)
     * @param row Source row (0-indexed)
     * @param w Width of region
     * @param h Height of region
     * @param tx Horizontal translation offset
     * @param ty Vertical translation offset
     * @return true if any cells changed
     */
    fun copy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int): Boolean {
        // Anything to do at all?
        if (w <= 0 || h <= 0) return false
        if (tx == 0 && ty == 0) return false
        // Loop over the target rectangle, starting from the directions away from
        // the source rectangle and copy the data. This way we ensure we don't
        // overwrite anything we still need to copy.
        val (dx0, dx1) = run {
            val pair = max(0, min(width - 1, col + tx + w - 1)) to max(0, min(width, col + tx))
            if (tx > 0) pair else pair.second to pair.first
        }
        val leftEdge = min(dx0, dx1) - 1
        if (leftEdge >= width - 1) return false // no work
        val (dy0, dy1) = run {
            val pair = max(0, min(height - 1, row + ty + h - 1)) to max(0, min(height, row + ty))
            if (ty > 0) pair else pair.second to pair.first
        }
        val sx = if (tx > 0) -1 else 1
        val sy = if (ty > 0) -1 else 1
        // Copy values to destination rectangle if there source is valid.
        var changed = false
        var ny = dy0
        while ((sy > 0 && ny <= dy1) || (sy < 0 && ny >= dy1)) {
            val nl = buffer[ny]
            val nc = color[ny]
            val oy = ny - ty
            if (oy >= 0 && oy < height) {
                val ol = buffer[oy]
                val oc = color[oy]
                var nx = dx0
                while ((sx > 0 && nx <= dx1) || (sx < 0 && nx >= dx1)) {
                    val ox = nx - tx
                    if (ox >= 0 && ox < width) {
                        changed = changed || (nl[nx] != ol[ox]) || (nc[nx] != oc[ox])
                        nl[nx] = ol[ox]
                        nc[nx] = oc[ox]
                        for (offset in 1 until FontUtils.wcwidth(nl[nx])) {
                            nl[nx + offset] = ' '.code
                            nc[nx + offset] = oc[nx]
                        }
                    }
                    nx += sx
                }
                // any wide chars along the left edge of the target rectangle need to be cleared
                // don't change their colors
                if (leftEdge >= 0 && FontUtils.wcwidth(nl[leftEdge]) > 1) {
                    nl[leftEdge] = ' '.code
                    changed = true
                }
            }
            ny += sy
        }
        return changed
    }

    /**
     * Copies a region from another buffer into this one.
     *
     * IMPORTANT: Unlike other methods, this uses 1-based indexing for Lua compatibility.
     * If the source buffer has a different color format, colors are automatically converted.
     *
     * @param col Destination column in this buffer (1-based, not 0-based!)
     * @param row Destination row in this buffer (1-based, not 0-based!)
     * @param w Width of region
     * @param h Height of region
     * @param src Source buffer
     * @param fromCol Source column (1-based, not 0-based!)
     * @param fromRow Source row (1-based, not 0-based!)
     * @return true if any cells changed
     */
    fun rawcopy(col: Int, row: Int, w: Int, h: Int, src: TextBufferData, fromCol: Int, fromRow: Int): Boolean {
        var changed = false
        val colIndex = col - 1
        val rowIndex = row - 1
        for (yOffset in 0 until h) {
            val dstCharLine = buffer[rowIndex + yOffset]
            val dstColorLine = color[rowIndex + yOffset]
            for (xOffset in 0 until w) {
                val srcChar = src.buffer[fromRow + yOffset - 1][fromCol + xOffset - 1]
                var srcColor = src.color[fromRow + yOffset - 1][fromCol + xOffset - 1].toUShort()

                if (this.format.depth != src.format.depth) {
                    val fg = PackedColor.Color(PackedColor.unpackForeground(srcColor, src.format))
                    val bg = PackedColor.Color(PackedColor.unpackBackground(srcColor, src.format))
                    srcColor = PackedColor.pack(fg, bg, format)
                }

                if (srcChar != dstCharLine[colIndex + xOffset] || srcColor != dstColorLine[colIndex + xOffset].toUShort()) {
                    changed = true
                    dstCharLine[colIndex + xOffset] = srcChar
                    dstColorLine[colIndex + xOffset] = srcColor.toShort()
                }
            }
        }

        return changed
    }

    /**
     * Sets a character at position x, handling wide characters correctly.
     *
     * Wide character handling:
     * - If the character is wide (2 columns), the next column is filled with space
     * - Wide characters cannot be placed in the rightmost column
     * - If placing this character would overwrite the second half of a wide char to the left, that char is cleared
     */
    private fun setChar(line: IntArray, lineColor: ShortArray, x: Int, c: Int) {
        if (FontUtils.wcwidth(c) > 1 && x >= line.size - 1) {
            // Don't allow setting wide chars in right-most col.
            return
        }
        line[x] = c
        lineColor[x] = packed.toShort()
        for (x1 in x + 1 until x + FontUtils.wcwidth(c)) {
            line[x1] = ' '.code
            lineColor[x1] = packed.toShort()
        }
        if (x > 0 && FontUtils.wcwidth(line[x - 1]) > 1) {
            // remove previous wide char (but don't change its color)
            line[x - 1] = ' '.code
        }
    }

    fun load(nbt: NBTTagCompound) {
        val maxResolution = max(Settings.screenResolutionsByTier.last().width, Settings.screenResolutionsByTier.last().height)
        val w = nbt.getInteger("width").coerceIn(1 .. maxResolution)
        val h = nbt.getInteger("height").coerceIn(1 .. maxResolution)
        setSize(w by h)

        val b = nbt.getTagList("buffer", NBT.TAG_STRING)
        for (i in 0 until min(h, b.tagCount())) {
            val value = b.getStringTagAt(i)
            val valueIt = value.codePoints().iterator()
            var j = 0
            while (j < buffer[i].size && valueIt.hasNext()) {
                buffer[i][j] = valueIt.nextInt()
                j++
            }
        }

        val depth = ColorDepth.values()[nbt.getInteger("depth").coerceIn(ColorDepth.values().indices)]
        _format = PackedColor.Depth.format(depth)
        _format.load(nbt)
        foreground = PackedColor.Color(nbt.getInteger("foreground").toUInt(), nbt.getBoolean("foregroundIsPalette"))
        background = PackedColor.Color(nbt.getInteger("background").toUInt(), nbt.getBoolean("backgroundIsPalette"))

        if (!NbtDataStream.getShortArray(nbt, "colors", color, w, h)) {
            NbtDataStream.getIntArrayLegacy(nbt, "color", color, w, h)
        }
    }

    fun save(nbt: NBTTagCompound) {
        nbt.setInteger("width", width)
        nbt.setInteger("height", height)

        val b = NBTTagList()
        for (i in 0 until height) {
            b.appendTag(NBTTagString(lineToString(i)))
        }
        nbt.setTag("buffer", b)

        nbt.setInteger("depth", _format.depth.ordinal)
        _format.save(nbt)
        nbt.setInteger("foreground", _foreground.value.toInt())
        nbt.setBoolean("foregroundIsPalette", _foreground.isPalette)
        nbt.setInteger("background", _background.value.toInt())
        nbt.setBoolean("backgroundIsPalette", _background.isPalette)

        NbtDataStream.setShortArray(nbt, "colors", color.flatMap { it.toList() }.toShortArray())
    }

    fun lineToString(y: Int): String {
        val b = StringBuilder()
        if (buffer.isNotEmpty()) {
            for (x in 0 until width) {
                b.appendCodePoint(buffer[y][x])
            }
        }
        return b.toString()
    }

    override fun toString(): String {
        val b = StringBuilder()
        if (buffer.isNotEmpty()) {
            for (x in 0 until width) {
                b.appendCodePoint(buffer[0][x])
            }
            for (y in 1 until height) {
                b.append('\n')
                for (x in 0 until width) {
                    b.appendCodePoint(buffer[y][x])
                }
            }
        }
        return b.toString()
    }
}
