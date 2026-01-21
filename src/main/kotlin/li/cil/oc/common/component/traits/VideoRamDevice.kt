package li.cil.oc.common.component.traits

import li.cil.oc.common.component.GpuTextBuffer
import li.cil.oc.util.PackedColor
import li.cil.oc.util.TextBuffer
import net.minecraft.nbt.NBTTagCompound

abstract class VideoRamDevice {
    private val internalBuffers = mutableMapOf<Int, GpuTextBuffer>()

    companion object {
        @JvmField
        val RESERVED_SCREEN_INDEX: Int = 0
    }

    val isEmpty: Boolean get() = internalBuffers.isEmpty()

    open fun onBufferRamDestroy(id: Int) {}

    fun bufferIndexes(): IntArray = internalBuffers.keys.toIntArray()

    fun addBuffer(ram: GpuTextBuffer): Boolean {
        val preexists = internalBuffers.containsKey(ram.id)
        internalBuffers[ram.id] = ram
        return preexists
    }

    fun removeBuffers(ids: IntArray): Int {
        var count = 0
        if (ids.isNotEmpty()) {
            for (id in ids) {
                if (internalBuffers.remove(id) != null) {
                    onBufferRamDestroy(id)
                    count++
                }
            }
        }
        return count
    }

    fun removeAllBuffers(): Int = removeBuffers(bufferIndexes())

    fun loadBuffer(address: String, id: Int, nbt: NBTTagCompound) {
        val src = TextBuffer(1, 1, PackedColor.SingleBitFormat)
        src.load(nbt)
        addBuffer(GpuTextBuffer.wrap(address, id, src))
    }

    fun getBuffer(id: Int): GpuTextBuffer? {
        return internalBuffers[id]
    }

    fun nextAvailableBufferIndex(): Int {
        var index = RESERVED_SCREEN_INDEX + 1
        while (internalBuffers.containsKey(index)) {
            index++
        }
        return index
    }

    fun calculateUsedMemory(): Int {
        var sum = 0
        for ((_, buffer) in internalBuffers) {
            sum += buffer.data.width * buffer.data.height
        }
        return sum
    }
}
