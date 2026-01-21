package li.cil.oc.server.fs

import li.cil.oc.api.fs.FileSystem as ApiFileSystem
import li.cil.oc.api.fs.Handle
import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants.NBT
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

interface InputStreamFileSystem : ApiFileSystem {
    val inputHandles: MutableMap<Int, InputHandle>

    // ----------------------------------------------------------------------- //

    override fun isReadOnly() = true

    override fun delete(path: String) = false

    override fun makeDirectory(path: String) = false

    override fun rename(from: String, to: String) = false

    override fun setLastModified(path: String, time: Long) = false

    // ----------------------------------------------------------------------- //

    override fun open(path: String, mode: Mode): Int {
        FileSystem.validatePath(path)
        return synchronized(this) {
            if (mode == Mode.Read && exists(path) && !isDirectory(path)) {
                val handle = generateSequence { (Math.random() * Int.MAX_VALUE).toInt() + 1 }
                    .filter { !inputHandles.containsKey(it) }
                    .first()
                val channel = openInputChannel(path)
                if (channel != null) {
                    inputHandles[handle] = InputHandle(this, handle, path, channel)
                    handle
                } else {
                    throw FileNotFoundException(path)
                }
            } else {
                throw FileNotFoundException(path)
            }
        }
    }

    override fun getHandle(handle: Int): Handle? = synchronized(this) {
        inputHandles[handle]
    }

    override fun close() {
        synchronized(this) {
            for (handle in inputHandles.values) {
                handle.close()
            }
            inputHandles.clear()
        }
    }

    // ----------------------------------------------------------------------- //

    override fun load(nbt: NBTTagCompound) {
        val handlesNbt = nbt.getTagList(InputTag, NBT.TAG_COMPOUND)
        for (i in 0 until handlesNbt.tagCount()) {
            val handleNbt = handlesNbt.getCompoundTagAt(i)
            val handle = handleNbt.getInteger(HandleTag)
            val path = handleNbt.getString(PathTag)
            val position = handleNbt.getLong(PositionTag)
            val channel = openInputChannel(path)
            if (channel != null) {
                val fileHandle = InputHandle(this, handle, path, channel)
                channel.position(position)
                inputHandles[handle] = fileHandle
            }
            // else: The source file seems to have disappeared since last time.
        }
    }

    override fun save(nbt: NBTTagCompound) {
        synchronized(this) {
            val handlesNbt = NBTTagList()
            for (file in inputHandles.values) {
                assert(file.channel.isOpen)
                val handleNbt = NBTTagCompound()
                handleNbt.setInteger(HandleTag, file.handle)
                handleNbt.setString(PathTag, file.path)
                handleNbt.setLong(PositionTag, file.position())
                handlesNbt.appendTag(handleNbt)
            }
            nbt.setTag(InputTag, handlesNbt)
        }
    }

    // ----------------------------------------------------------------------- //

    fun openInputChannel(path: String): InputChannel?

    interface InputChannel : ReadableByteChannel {
        override fun isOpen(): Boolean

        override fun close()

        fun position(): Long

        fun position(newPosition: Long): Long

        fun read(dst: ByteArray): Int

        override fun read(dst: ByteBuffer): Int {
            return if (dst.hasArray()) {
                read(dst.array())
            } else {
                val count = maxOf(0, dst.limit() - dst.position())
                val buffer = ByteArray(count)
                val n = read(buffer)
                if (n > 0) dst.put(buffer, 0, n)
                n
            }
        }
    }

    open class InputStreamChannel(val inputStream: InputStream) : InputChannel {
        private var _isOpen = true

        private var _position = 0L

        override fun isOpen() = _isOpen

        override fun close() {
            if (_isOpen) {
                _isOpen = false
                inputStream.close()
            }
        }

        override fun position() = _position

        override fun position(newPosition: Long): Long {
            inputStream.reset()
            _position = inputStream.skip(newPosition)
            return _position
        }

        override fun read(dst: ByteArray): Int {
            val read = inputStream.read(dst)
            _position += read
            return read
        }

        override fun read(dst: ByteBuffer): Int {
            return if (dst.hasArray()) {
                read(dst.array())
            } else {
                val count = maxOf(0, dst.limit() - dst.position())
                val buffer = ByteArray(count)
                val n = read(buffer)
                if (n > 0) dst.put(buffer, 0, n)
                n
            }
        }
    }

    // ----------------------------------------------------------------------- //

    class InputHandle(
        val owner: InputStreamFileSystem,
        val handle: Int,
        val path: String,
        val channel: InputChannel
    ) : Handle {
        override fun position() = channel.position()

        override fun length() = owner.size(path)

        override fun close() {
            if (channel.isOpen()) {
                owner.inputHandles.remove(handle)
                channel.close()
            }
        }

        override fun read(into: ByteArray) = channel.read(into)

        override fun seek(to: Long) = channel.position(to)

        override fun write(value: ByteArray) {
            throw IOException("bad file descriptor")
        }
    }

    companion object {
        private const val InputTag = "input"
        private const val HandleTag = "handle"
        private const val PathTag = "path"
        private const val PositionTag = "position"
    }
}
