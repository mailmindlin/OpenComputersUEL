package li.cil.oc.common.component

import li.cil.oc.api.internal.TextBuffer as InternalTextBuffer
import java.io.InvalidObjectException

import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.nbt.NBTTagCompound
import li.cil.oc.common.component.TextBuffer as ComponentTextBuffer
import li.cil.oc.api.internal.TextBuffer.ColorDepth
import li.cil.oc.common.component.traits.TextBufferProxy
import li.cil.oc.common.component.traits.VideoRamRasterizer
import li.cil.oc.util.TextBuffer as UtilTextBuffer

class GpuTextBuffer(val owner: String, val id: Int, override val data: UtilTextBuffer) : TextBufferProxy {

    // the gpu ram does not join nor is searchable to the network
    // this field is required because the api TextBuffer is an Environment
    override fun node(): Node {
        throw InvalidObjectException("GpuTextBuffers do not have nodes")
    }

    override fun getMaximumWidth(): Int = data.width
    override fun getMaximumHeight(): Int = data.height
    override fun getViewportWidth(): Int = data.height
    override fun getViewportHeight(): Int = data.width

    var dirty: Boolean = true
    override fun onBufferSet(col: Int, row: Int, s: String, vertical: Boolean) { dirty = true }
    override fun onBufferColorChange() { dirty = true }
    override fun onBufferCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int) { dirty = true }
    override fun onBufferFill(col: Int, row: Int, w: Int, h: Int, c: Int) { dirty = true }

    override fun load(nbt: NBTTagCompound) {
        // the data is initially dirty because other devices don't know about it yet
        data.load(nbt)
        dirty = true
    }

    override fun save(nbt: NBTTagCompound) {
        data.save(nbt)
        dirty = false
    }

    override fun setEnergyCostPerTick(value: Double) {}
    override fun getEnergyCostPerTick(): Double = 0.0
    override fun setPowerState(value: Boolean) {}
    override fun getPowerState(): Boolean = false
    override fun setMaximumResolution(width: Int, height: Int) {}
    override fun setAspectRatio(width: Double, height: Double) {}
    override fun getAspectRatio(): Double = 1.0
    override fun setResolution(width: Int, height: Int): Boolean = false
    override fun setViewport(width: Int, height: Int): Boolean = false
    override fun setMaximumColorDepth(depth: ColorDepth) {}
    override fun getMaximumColorDepth(): ColorDepth = data.format.depth
    override fun renderText(): Boolean = false
    override fun renderWidth(): Int = 0
    override fun renderHeight(): Int = 0
    override fun setRenderingEnabled(enabled: Boolean) {}
    override fun isRenderingEnabled(): Boolean = false
    override fun keyDown(character: Char, code: Int, player: EntityPlayer) {}
    override fun keyUp(character: Char, code: Int, player: EntityPlayer) {}
    override fun clipboard(value: String, player: EntityPlayer) {}
    override fun mouseDown(x: Double, y: Double, button: Int, player: EntityPlayer) {}
    override fun mouseDrag(x: Double, y: Double, button: Int, player: EntityPlayer) {}
    override fun mouseUp(x: Double, y: Double, button: Int, player: EntityPlayer) {}
    override fun mouseScroll(x: Double, y: Double, delta: Int, player: EntityPlayer) {}
    override fun canUpdate(): Boolean = false
    override fun update() {}
    override fun onConnect(node: Node) {}
    override fun onDisconnect(node: Node) {}
    override fun onMessage(message: Message) {}

    companion object {
        @JvmStatic
        fun wrap(owner: String, id: Int, data: UtilTextBuffer): GpuTextBuffer = GpuTextBuffer(owner, id, data)

        @JvmStatic
        fun bitblt(dst: InternalTextBuffer, col: Int, row: Int, w: Int, h: Int, src: InternalTextBuffer, fromCol: Int, fromRow: Int) {
            val x = col - 1
            val y = row - 1
            val fx = fromCol - 1
            val fy = fromRow - 1
            var adjustedDstX = x
            var adjustedDstY = y
            var adjustedWidth = w
            var adjustedHeight = h
            var adjustedSourceX = fx
            var adjustedSourceY = fy

            if (x < 0) {
                adjustedWidth += x
                adjustedSourceX -= x
                adjustedDstX = 0
            }

            if (y < 0) {
                adjustedHeight += y
                adjustedSourceY -= y
                adjustedDstY = 0
            }

            if (adjustedSourceX < 0) {
                adjustedWidth += adjustedSourceX
                adjustedDstX -= adjustedSourceX
                adjustedSourceX = 0
            }

            if (adjustedSourceY < 0) {
                adjustedHeight += adjustedSourceY
                adjustedDstY -= adjustedSourceY
                adjustedSourceY = 0
            }

            adjustedWidth -= maxOf(0, (adjustedDstX + adjustedWidth) - dst.width)
            adjustedWidth -= maxOf(0, (adjustedSourceX + adjustedWidth) - src.width)

            adjustedHeight -= maxOf(0, (adjustedDstY + adjustedHeight) - dst.height)
            adjustedHeight -= maxOf(0, (adjustedSourceY + adjustedHeight) - src.height)

            // anything left?
            if (adjustedWidth <= 0 || adjustedHeight <= 0) {
                return
            }

            when (dst) {
                is ComponentTextBuffer -> when (src) {
                    is GpuTextBuffer -> writeVramToScreen(dst, adjustedDstX, adjustedDstY, adjustedWidth, adjustedHeight, src, adjustedSourceX, adjustedSourceY)
                    else -> throw UnsupportedOperationException("Source buffer does not support bitblt operations to a screen")
                }
                is GpuTextBuffer -> when (src) {
                    is TextBufferProxy -> writeToVram(dst, adjustedDstX, adjustedDstY, adjustedWidth, adjustedHeight, src, adjustedSourceX, adjustedSourceY)
                    else -> throw UnsupportedOperationException("Source buffer does not support bitblt operations")
                }
                else -> throw UnsupportedOperationException("Destination buffer does not support bitblt operations")
            }
        }

        @JvmStatic
        fun writeVramToScreen(dstScreen: ComponentTextBuffer, x: Int, y: Int, w: Int, h: Int, srcRam: GpuTextBuffer, fx: Int, fy: Int): Boolean {
            if (dstScreen.data.rawcopy(x + 1, y + 1, w, h, srcRam.data, fx + 1, fy + 1)) {
                // rawcopy returns true only if data was modified
                dstScreen.addBuffer(srcRam)
                dstScreen.onBufferBitBlt(x + 1, y + 1, w, h, srcRam, fx + 1, fy + 1)
                return true
            }
            return false
        }

        @JvmStatic
        fun writeToVram(dstRam: GpuTextBuffer, x: Int, y: Int, w: Int, h: Int, src: TextBufferProxy, fx: Int, fy: Int): Boolean {
            if (dstRam.data.rawcopy(x + 1, y + 1, w, h, src.data, fx + 1, fy + 1)) {
                dstRam.dirty = true
                return true
            }
            return false
        }
    }
}

object ClientGpuTextBufferHandler {
    @JvmStatic
    fun bitblt(dst: InternalTextBuffer, col: Int, row: Int, w: Int, h: Int, owner: String, srcId: Int, fromCol: Int, fromRow: Int) {
        if (dst is VideoRamRasterizer) {
            val buffer = dst.getBuffer(owner, srcId)
            if (buffer != null) {
                GpuTextBuffer.bitblt(dst, col, row, w, h, buffer, fromCol, fromRow)
            }
            // else ignore - got a bitblt for a missing buffer
        }
        // else ignore - weird packet handler called this, should only happen for video ram aware devices
    }

    @JvmStatic
    fun removeBuffer(buffer: InternalTextBuffer, owner: String, id: Int): Boolean {
        return if (buffer is VideoRamRasterizer) {
            buffer.removeBuffer(owner, id)
        } else {
            false // ignore, not compatible with bitblts
        }
    }

    @JvmStatic
    fun loadBuffer(buffer: InternalTextBuffer, owner: String, id: Int, nbt: NBTTagCompound): Boolean {
        return if (buffer is VideoRamRasterizer) {
            buffer.loadBuffer(owner, id, nbt)
        } else {
            false // ignore, not compatible with bitblts
        }
    }
}
