package li.cil.oc.server.fs

import li.cil.oc.Settings
import li.cil.oc.api.fs.FileSystem
import li.cil.oc.api.fs.Handle
import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound
import java.io.IOException
import java.util.WeakHashMap
import kotlin.jvm.Throws

class Capacity(protected val wrapped: FileSystem, private val capacity: Long): FileSystem by wrapped {
    class CapacityException(val capacity: Long, val used: Long, val space: Long): IOException("not enough space ($capacity, $used, $space)") {}
    init {
        check(capacity >= 0L) { "Capacity must not be negative" }
    }
    private val openWriteHandles = mutableSetOf<Int>()
    private val writeHandleCache = WeakHashMap<Handle, CountingOutputHandle>()

    private var used: Long = computeSize("/")
    // Used when loading data from disk to virtual file systems, to allow
    // exceeding the actual capacity of a file system.
    private var ignoreCapacity = false

    private fun releaseCapacity(freed: Long) {
        used = (used - freed).coerceAtLeast(0)
    }
    private fun acquireCapacity(space: Long) {
        used = (used + space).coerceAtLeast(0)
    }

    @Throws(CapacityException::class)
    private fun assertCapacity(space: Long) {
        if (capacity - used < space && !ignoreCapacity)
            throw CapacityException(capacity, used, space)
    }

    // ----------------------------------------------------------------------- //

    override fun spaceTotal() = capacity
    override fun spaceUsed() = used

    // ----------------------------------------------------------------------- //

    override fun delete(path: String): Boolean {
        val freed = Settings.get.fileCost + size(path)
        if (!wrapped.delete(path))
            return false
        releaseCapacity(freed)
        return true
    }

    override fun rename(from: String, to: String): Boolean {
        if (!exists(to))
            return wrapped.rename(from, to)

        // 'to' is being deleted
        val freed = Settings.get.fileCost + size(to)
        if (!wrapped.rename(from, to))
            return false

        releaseCapacity(freed)
        return true
    }

    override fun makeDirectory(path: String): Boolean {
        val space = Settings.get.fileCost.toLong()
        assertCapacity(space)

        if (!wrapped.makeDirectory(path))
            return false

        acquireCapacity(space)
        return true
    }

    // ----------------------------------------------------------------------- //

    override fun close() {
        wrapped.close()
        used = computeSize("/")
    }

    // ----------------------------------------------------------------------- //

    override fun load(nbt: NBTTagCompound) {
        try {
            ignoreCapacity = true
            wrapped.load(nbt)
        } finally {
            ignoreCapacity = false
        }

        used = computeSize("/")
    }

    override fun save(nbt: NBTTagCompound) {
        wrapped.save(nbt)

        // For the tooltip.
        nbt.setLong("capacity.used", used)
    }

    override fun open(path: String, mode: Mode): Int {
        if (mode == Mode.Read)
            return wrapped.open(path, mode)

        val delta = when {
            !exists(path) -> Settings.get.fileCost.toLong() // File creation.
            mode == Mode.Write -> -size(path) // Overwrite clears
            else -> 0L // append no change
        }
        assertCapacity(delta)
        val handle = wrapped.open(path, mode)
        this.openWriteHandles.add(handle)
        this.acquireCapacity(delta)
        return handle
    }

    override fun getHandle(handle: Int): Handle? {
        val handleObj = wrapped.getHandle(handle) ?: return null
        synchronized(this) {
            var handleRef = this.writeHandleCache[handleObj]
            if (handleRef == null) {
                handleRef = CountingOutputHandle(handleObj)
                this.writeHandleCache[handleObj] = handleRef
            }
            return handleRef
        }
    }

    // ----------------------------------------------------------------------- //

    /*override fun openInputChannel(path: String): InputChannel? = wrapped.openInputChannel(path)
    override fun openOutputHandle(id: Int, path: String, mode: Mode): OutputHandle? {
        val delta = when {
            exists(path) -> if (mode == Mode.Write) -size(path) else 0 // Overwrite clears, append no change
            else -> Settings.get.fileCost.toLong() // File creation.
        }
        assertCapacity(delta)
        val stream = wrapped.openOutputHandle(id, path, mode) ?: return null
        this.acquireCapacity(delta)
        if (mode == Mode.Append)
            stream.seek(stream.length())
        return CountingOutputHandle(stream)
    }*/

    // ----------------------------------------------------------------------- //

    private fun computeSize(path: String): Long =
        Settings.get.fileCost +
                size(path) +
                // Add child cost
                (path.takeIf(::isDirectory)
                    ?.let(::list)
                    ?.sumOf { child -> computeSize(path + child) }
                ?: 0L)

    private inner class CountingOutputHandle(private val inner: Handle) : Handle by inner {
        override fun write(b: ByteArray) {
            assertCapacity(b.size.toLong())
            inner.write(b)
            acquireCapacity(b.size.toLong())
        }
    }
}
