package li.cil.oc.server.fs

import li.cil.oc.Settings
import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound
import java.io.IOException

interface Capacity : OutputStreamFileSystem {
    var used: Long

    var ignoreCapacity: Boolean

    val capacity: Long

    // ----------------------------------------------------------------------- //

    override fun spaceTotal() = capacity

    override fun spaceUsed() = used

    // ----------------------------------------------------------------------- //

    override fun delete(path: String): Boolean {
        val freed = Settings.get.fileCost + size(path)
        return if (super.delete(path)) {
            used = maxOf(0, used - freed)
            true
        } else {
            false
        }
    }

    override fun rename(from: String, to: String): Boolean {
        return if (exists(to)) {
            val freed = Settings.get.fileCost + size(to)
            if (super.rename(from, to)) {
                used = maxOf(0, used - freed)
                true
            } else {
                false
            }
        } else {
            super.rename(from, to)
        }
    }

    override fun makeDirectory(path: String): Boolean {
        if (capacity - used < Settings.get.fileCost && !ignoreCapacity) {
            throw IOException("not enough space")
        }
        return if (super.makeDirectory(path)) {
            used += Settings.get.fileCost
            true
        } else {
            false
        }
    }

    // ----------------------------------------------------------------------- //

    override fun close() {
        super.close()
        used = computeSize("/")
    }

    // ----------------------------------------------------------------------- //

    override fun load(nbt: NBTTagCompound) {
        try {
            ignoreCapacity = true
            super.load(nbt)
        } finally {
            ignoreCapacity = false
        }

        used = computeSize("/")
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)

        // For the tooltip.
        nbt.setLong("capacity.used", used)
    }

    // ----------------------------------------------------------------------- //

    fun capacityOpenOutputHandle(id: Int, path: String, mode: Mode, superOpenOutputHandle: (Int, String, Mode) -> OutputStreamFileSystem.OutputHandle?): OutputStreamFileSystem.OutputHandle? {
        val delta = when {
            exists(path) -> if (mode == Mode.Write) -size(path) else 0 // Overwrite clears, append no change
            else -> Settings.get.fileCost // File creation.
        }
        if (capacity - used < delta && !ignoreCapacity) {
            throw IOException("not enough space")
        }
        val stream = superOpenOutputHandle(id, path, mode)
        return if (stream != null) {
            used = maxOf(0, used + delta)
            if (mode == Mode.Append) {
                stream.seek(stream.length())
            }
            CountingOutputHandle(this, stream)
        } else {
            null
        }
    }

    // ----------------------------------------------------------------------- //

    fun computeSize(path: String): Long =
        Settings.get.fileCost +
            size(path) +
            if (isDirectory(path)) {
                (list(path) ?: emptyArray()).fold(0L) { acc, child -> acc + computeSize(path + child) }
            } else {
                0L
            }

    class CountingOutputHandle(
        override val owner: Capacity,
        private val inner: OutputStreamFileSystem.OutputHandle
    ) : OutputStreamFileSystem.OutputHandle(inner.owner, inner.handle, inner.path) {
        override val isClosed: Boolean
            get() = inner.isClosed

        override fun length() = inner.length()

        override fun position() = inner.position()

        override fun close() = inner.close()

        override fun seek(to: Long) = inner.seek(to)

        override fun write(b: ByteArray) {
            if (owner.capacity - owner.used < b.size && !owner.ignoreCapacity)
                throw IOException("not enough space")
            inner.write(b)
            owner.used = owner.used + b.size
        }
    }
}
