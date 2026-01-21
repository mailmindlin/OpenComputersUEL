package li.cil.oc.server.fs

import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants.NBT
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

interface VirtualFileSystem : OutputStreamFileSystem {
    val root: VirtualDirectory

    // ----------------------------------------------------------------------- //

    override fun exists(path: String): Boolean =
        root.get(segments(path)) != null

    override fun isDirectory(path: String): Boolean {
        val obj = root.get(segments(path))
        return obj?.isDirectory ?: false
    }

    override fun size(path: String): Long {
        val obj = root.get(segments(path))
        return obj?.size ?: 0L
    }

    override fun lastModified(path: String): Long {
        val obj = root.get(segments(path))
        return obj?.lastModified ?: 0L
    }

    override fun list(path: String): Array<String>? {
        val obj = root.get(segments(path))
        return if (obj is VirtualDirectory) obj.list() else null
    }

    // ----------------------------------------------------------------------- //

    override fun delete(path: String): Boolean {
        val parts = segments(path)
        if (parts.isEmpty()) return true
        val parent = root.get(parts.dropLast(1))
        return if (parent is VirtualDirectory) {
            parent.delete(parts.last())
        } else {
            false
        }
    }

    override fun makeDirectory(path: String): Boolean {
        val parts = segments(path)
        if (parts.isEmpty()) return false
        val parent = root.get(parts.dropLast(1))
        return if (parent is VirtualDirectory) {
            parent.makeDirectory(parts.last())
        } else {
            false
        }
    }

    override fun rename(from: String, to: String): Boolean {
        if (from == "" || !exists(from)) throw FileNotFoundException(from)
        val segmentsTo = segments(to)
        val toParent = root.get(segmentsTo.dropLast(1))
        return if (toParent is VirtualDirectory) {
            val toName = segmentsTo.last()
            val segmentsFrom = segments(from)
            val fromParent = root.get(segmentsFrom.dropLast(1)) as VirtualDirectory
            val fromName = segmentsFrom.last()
            val obj = fromParent.children[fromName]!!

            if (toParent.get(listOf(toName)) != null) {
                toParent.delete(toName)
            }

            fromParent.children.remove(fromName)
            fromParent.lastModified = System.currentTimeMillis()

            toParent.children[toName] = obj
            toParent.lastModified = System.currentTimeMillis()

            obj.lastModified = System.currentTimeMillis()
            true
        } else {
            false
        }
    }

    override fun setLastModified(path: String, time: Long): Boolean {
        val obj = root.get(segments(path))
        return if (obj != null && time >= 0) {
            obj.lastModified = time
            true
        } else {
            false
        }
    }

    // ----------------------------------------------------------------------- //

    override fun openInputChannel(path: String): InputStreamFileSystem.InputChannel? {
        val obj = root.get(segments(path))
        return if (obj is VirtualFile) {
            val stream = obj.openInputStream()
            if (stream != null) InputStreamChannel(stream) else null
        } else {
            null
        }
    }

    override fun openOutputHandle(id: Int, path: String, mode: Mode): OutputStreamFileSystem.OutputHandle? {
        val parts = segments(path)
        if (parts.isEmpty()) return null
        val parent = root.get(parts.dropLast(1))
        return if (parent is VirtualDirectory) {
            val file = parent.touch(parts.last())
            file?.openOutputHandle(this, id, path, mode)
        } else {
            null
        }
    }

    // ----------------------------------------------------------------------- //

    override fun load(nbt: NBTTagCompound) {
        if (this !is Buffered) root.load(nbt)
        super.load(nbt) // Last to ensure streams can be re-opened.
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt) // First to allow flushing.
        if (this !is Buffered) root.save(nbt)
    }

    // ----------------------------------------------------------------------- //

    fun segments(path: String): List<String> =
        FileSystem.validatePath(path).split("/").filter { it.isNotEmpty() }

    // ----------------------------------------------------------------------- //

    interface VirtualObject {
        val isDirectory: Boolean

        val size: Long

        var lastModified: Long

        fun load(nbt: NBTTagCompound) {
            if (nbt.hasKey("lastModified"))
                lastModified = nbt.getLong("lastModified")
        }

        fun save(nbt: NBTTagCompound) {
            nbt.setLong("lastModified", lastModified)
        }

        fun get(path: List<String>): VirtualObject? =
            if (path.isEmpty()) this else null

        fun canDelete(): Boolean
    }

    // ----------------------------------------------------------------------- //

    class VirtualFile : VirtualObject {
        val data = mutableListOf<Byte>()

        var handle: VirtualOutputHandle? = null

        override val isDirectory = false

        override val size: Long
            get() = data.size.toLong()

        override var lastModified = System.currentTimeMillis()

        fun openInputStream(): InputStream? = VirtualFileInputStream(this)

        fun openOutputHandle(owner: OutputStreamFileSystem, id: Int, path: String, mode: Mode): VirtualOutputHandle? {
            if (handle != null) return null
            if (mode == Mode.Write) {
                data.clear()
                lastModified = System.currentTimeMillis()
            }
            handle = VirtualOutputHandle(this, owner, id, path)
            return handle
        }

        override fun load(nbt: NBTTagCompound) {
            super.load(nbt)
            data.clear()
            data.addAll(nbt.getByteArray("data").toList())
        }

        override fun save(nbt: NBTTagCompound) {
            super.save(nbt)
            nbt.setByteArray("data", data.toByteArray())
        }

        override fun canDelete() = handle == null
    }

    // ----------------------------------------------------------------------- //

    class VirtualDirectory : VirtualObject {
        val children = mutableMapOf<String, VirtualObject>()

        override val isDirectory = true

        override val size = 0L

        override var lastModified = System.currentTimeMillis()

        fun list(): Array<String> = children.map { (childName, child) ->
            if (child.isDirectory) "$childName/" else childName
        }.toTypedArray()

        fun makeDirectory(name: String): Boolean {
            if (children.containsKey(name)) return false
            children[name] = VirtualDirectory()
            lastModified = System.currentTimeMillis()
            return true
        }

        fun delete(name: String): Boolean {
            val child = children[name]
            return if (child != null && child.canDelete()) {
                children.remove(name)
                lastModified = System.currentTimeMillis()
                true
            } else {
                false
            }
        }

        fun touch(name: String): VirtualFile? {
            val existing = children[name]
            return when (existing) {
                is VirtualFile -> existing
                null -> {
                    val child = VirtualFile()
                    children[name] = child
                    lastModified = System.currentTimeMillis()
                    child
                }
                else -> null // Directory.
            }
        }

        override fun load(nbt: NBTTagCompound) {
            super.load(nbt)
            val childrenNbt = nbt.getTagList(ChildrenTag, NBT.TAG_COMPOUND)
            for (i in 0 until childrenNbt.tagCount()) {
                val childNbt = childrenNbt.getCompoundTagAt(i)
                val child: VirtualObject = if (childNbt.getBoolean(IsDirectoryTag)) {
                    VirtualDirectory()
                } else {
                    VirtualFile()
                }
                child.load(childNbt)
                children[childNbt.getString(NameTag)] = child
            }
        }

        override fun save(nbt: NBTTagCompound) {
            super.save(nbt)
            val childrenNbt = NBTTagList()
            for ((childName, child) in children) {
                val childNbt = NBTTagCompound()
                childNbt.setBoolean(IsDirectoryTag, child.isDirectory)
                childNbt.setString(NameTag, childName)
                child.save(childNbt)
                childrenNbt.appendTag(childNbt)
            }
            nbt.setTag(ChildrenTag, childrenNbt)
        }

        override fun get(path: List<String>): VirtualObject? {
            if (path.isEmpty()) return this
            val child = children[path.first()]
            return child?.get(path.drop(1))
        }

        override fun canDelete() = children.isEmpty()

        companion object {
            private const val ChildrenTag = "children"
            private const val IsDirectoryTag = "isDirectory"
            private const val NameTag = "name"
        }
    }

    // ----------------------------------------------------------------------- //

    class VirtualFileInputStream(private val file: VirtualFile) : InputStream() {
        private var isClosed = false

        private var position = 0

        override fun available(): Int =
            if (isClosed) 0
            else maxOf(file.data.size - position, 0)

        override fun close() {
            isClosed = true
        }

        override fun read(): Int {
            if (!isClosed) {
                if (available() == 0) return -1
                position += 1
                return file.data[position - 1].toInt() and 0xFF
            } else {
                throw IOException("file is closed")
            }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (!isClosed) {
                val count = available()
                if (count == 0) return -1
                val n = minOf(len, count)
                for (i in 0 until n) {
                    b[off + i] = file.data[position + i]
                }
                position += n
                return n
            } else {
                throw IOException("file is closed")
            }
        }

        override fun reset() {
            if (!isClosed) {
                position = 0
            } else {
                throw IOException("file is closed")
            }
        }

        override fun skip(n: Long): Long {
            if (!isClosed) {
                position = minOf((position + n).toInt(), Int.MAX_VALUE)
                return position.toLong()
            } else {
                throw IOException("file is closed")
            }
        }
    }

    // ----------------------------------------------------------------------- //

    class VirtualOutputHandle(
        val file: VirtualFile,
        owner: OutputStreamFileSystem,
        handle: Int,
        path: String
    ) : OutputStreamFileSystem.OutputHandle(owner, handle, path) {
        override fun length() = file.size

        private var _position: Long = file.data.size.toLong()

        override fun position() = _position

        override fun close() {
            if (!isClosed) {
                super.close()
                assert(file.handle === this)
                file.handle = null
            }
        }

        override fun seek(to: Long): Long {
            if (to < 0) throw IOException("invalid offset")
            _position = to
            return _position
        }

        override fun write(value: ByteArray) {
            if (!isClosed) {
                val pos = _position.toInt()
                // Extend the list if needed
                val neededSize = pos + value.size
                while (file.data.size < neededSize) {
                    file.data.add(0)
                }
                for (i in value.indices) {
                    file.data[pos + i] = value[i]
                }
                _position += value.size
                file.lastModified = System.currentTimeMillis()
            } else {
                throw IOException("file is closed")
            }
        }
    }
}
