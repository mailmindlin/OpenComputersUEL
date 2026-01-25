package li.cil.oc.server.fs

import li.cil.oc.api.fs.Handle
import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants.NBT
import java.io.FileNotFoundException
import java.io.IOException

interface OutputStreamFileSystem : InputStreamFileSystem {
    val outputHandles: MutableMap<Int, OutputHandle>

    // ----------------------------------------------------------------------- //

    override fun isReadOnly() = false

    // ----------------------------------------------------------------------- //

    override fun open(path: String, mode: Mode): Int = synchronized(this) {
        when (mode) {
            Mode.Read -> super.open(path, mode)
            else -> {
                FileSystem.validatePath(path)
                if (!isDirectory(path)) {
                    val handle = generateSequence { (Math.random() * Int.MAX_VALUE).toInt() + 1 }
                        .filter { !outputHandles.containsKey(it) }
                        .first()
                    val fileHandle = openOutputHandle(handle, path, mode)
                    if (fileHandle != null) {
                        outputHandles[handle] = fileHandle
                        handle
                    } else {
                        throw FileNotFoundException(path)
                    }
                } else {
                    throw FileNotFoundException(path)
                }
            }
        }
    }

    override fun getHandle(handle: Int): Handle? = synchronized(this) {
        super.getHandle(handle) ?: outputHandles[handle]
    }

    override fun close() {
        synchronized(this) {
            super.close()
            for (handle in outputHandles.values) {
                handle.close()
            }
            outputHandles.clear()
        }
    }

    // ----------------------------------------------------------------------- //

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

    fun openOutputHandle(id: Int, path: String, mode: Mode): OutputHandle?

    // ----------------------------------------------------------------------- //

    abstract class OutputHandle(
        open val owner: OutputStreamFileSystem,
        val handle: Int,
        val path: String
    ) : Handle {
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

    companion object {
        private const val OutputTag = "output"
        private const val HandleTag = "handle"
        private const val PathTag = "path"
    }
}
