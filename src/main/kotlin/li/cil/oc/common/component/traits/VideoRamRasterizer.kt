package li.cil.oc.common.component.traits

import li.cil.oc.common.component.GpuTextBuffer
import li.cil.oc.util.PackedColor
import li.cil.oc.util.TextBuffer
import net.minecraft.nbt.NBTTagCompound

interface VideoRamRasterizer {
    class VirtualRamDevice(val owner: String) : VideoRamDevice()

    val internalRasterizerBuffers: MutableMap<String, VirtualRamDevice>

    fun onBufferRamInit(ram: GpuTextBuffer)
    fun onBufferBitBlt(col: Int, row: Int, w: Int, h: Int, ram: GpuTextBuffer, fromCol: Int, fromRow: Int)
    fun onBufferRamDestroy(ram: GpuTextBuffer)

    fun addBuffer(ram: GpuTextBuffer): Boolean {
        var gpu = internalRasterizerBuffers[ram.owner]
        if (gpu == null) {
            gpu = VirtualRamDevice(ram.owner)
            internalRasterizerBuffers[ram.owner] = gpu
        }
        val preexists: Boolean = gpu.addBuffer(ram)
        if (!preexists || ram.dirty) {
            onBufferRamInit(ram)
        }
        return preexists
    }

    fun removeBuffer(owner: String, id: Int): Boolean {
        val gpu = internalRasterizerBuffers[owner]
        if (gpu != null) {
            val ram = gpu.getBuffer(id)
            if (ram != null) {
                onBufferRamDestroy(ram)
                return gpu.removeBuffers(intArrayOf(id)) == 1
            }
        }
        return false
    }

    fun removeAllBuffers(owner: String): Int {
        var count = 0
        val gpu = internalRasterizerBuffers[owner]
        if (gpu != null) {
            val ids = gpu.bufferIndexes()
            for (id in ids) {
                if (removeBuffer(owner, id)) {
                    count++
                }
            }
        }
        return count
    }

    fun removeAllBuffers(): Int {
        var count = 0
        for ((owner, _) in internalRasterizerBuffers) {
            count += removeAllBuffers(owner)
        }
        return count
    }

    fun loadBuffer(owner: String, id: Int, nbt: NBTTagCompound): Boolean {
        val src = TextBuffer(1, 1, PackedColor.SingleBitFormat)
        src.load(nbt)
        return addBuffer(GpuTextBuffer.wrap(owner, id, src))
    }

    fun getBuffer(owner: String, id: Int): GpuTextBuffer? {
        val gpu = internalRasterizerBuffers[owner]
        return gpu?.getBuffer(id)
    }
}
