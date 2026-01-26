package li.cil.oc.server.fs

import li.cil.oc.api.fs.Handle
import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants.NBT
import java.io.Closeable
import java.io.FileNotFoundException
import java.io.IOException

abstract class OutputStreamFileSystem : InputStreamFileSystem() {
    protected val outputHandles: MutableMap<Int, OutputHandle> = mutableMapOf()

    // ----------------------------------------------------------------------- //

    override fun isReadOnly() = false

    // ----------------------------------------------------------------------- //

    override fun open(path: String, mode: Mode): Int {
        FileSystem.validatePath(path)

        if (mode == Mode.Read)
            return super.open(path, mode)

        return synchronized(this) {
            if (isDirectory(path))
                throw FileNotFoundException(path)
            val handle = generateSequence { (Math.random() * Int.MAX_VALUE).toInt() + 1 }
                .first { it !in outputHandles }
            val fileHandle = openOutputHandle(handle, path, mode) ?: throw FileNotFoundException(path)
            outputHandles[handle] = fileHandle
            handle
        }
    }

    override fun getHandle(handle: Int): Handle? = synchronized(this) {
        super.getHandle(handle) ?: outputHandles[handle]
    }

    override fun close() {
        synchronized(this) {
            super.close()
            outputHandles.values.forEach(Handle::close)
            outputHandles.clear()
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private const val OutputTag = "output"
        private const val HandleTag = "handle"
        private const val PathTag = "path"
    }

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)

        val handlesNbt = nbt.getTagList(OutputTag, NBT.TAG_COMPOUND)
        for (i in 0 until handlesNbt.tagCount()) {
            val handleNbt = handlesNbt.getCompoundTagAt(i)
            val handle = handleNbt.getInteger(HandleTag)
            val path = handleNbt.getString(PathTag)
            val fileHandle = openOutputHandle(handle, path, Mode.Append)
            if (fileHandle != null) {
                outputHandles[handle] = fileHandle
            }
            // else: The source file seems to have changed since last time.
        }
    }

    override fun save(nbt: NBTTagCompound) {
        synchronized(this) {
            super.save(nbt)

            val handlesNbt = NBTTagList()
            for (file in outputHandles.values) {
                assert(!file.isClosed)
                val handleNbt = NBTTagCompound()
                handleNbt.setInteger(HandleTag, file.handle)
                handleNbt.setString(PathTag, file.path)
                handlesNbt.appendTag(handleNbt)
            }
            nbt.setTag(OutputTag, handlesNbt)
        }
    }

    // ----------------------------------------------------------------------- //

    internal abstract fun openOutputHandle(id: Int, path: String, mode: Mode): OutputHandle?

    // ----------------------------------------------------------------------- //

    abstract class OutputHandle(
        open val owner: OutputStreamFileSystem,
        val handle: Int,
        val path: String
    ) : Handle, Closeable {
        protected var _isClosed = false

        open val isClosed: Boolean
            get() = _isClosed

        override fun close() {
            if (!isClosed) {
                _isClosed = true
                owner.outputHandles.remove(handle)
            }
        }

        override fun read(into: ByteArray): Int {
            throw IOException("bad file descriptor")
        }

        override fun seek(to: Long): Long {
            throw IOException("bad file descriptor")
        }
    }
    abstract class WrappingOutputHandle(
        protected val inner: OutputHandle
    ) : OutputHandle(inner.owner, inner.handle, inner.path) {
        override val isClosed: Boolean
            get() = inner.isClosed

        override fun length() = inner.length()
        override fun position() = inner.position()
        override fun close() = inner.close()
        override fun seek(to: Long) = inner.seek(to)
        override fun write(b: ByteArray) = inner.write(b)
    }
}
