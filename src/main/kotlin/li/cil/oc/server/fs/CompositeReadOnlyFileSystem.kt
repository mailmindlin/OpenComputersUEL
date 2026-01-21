package li.cil.oc.server.fs

import li.cil.oc.api.fs.FileSystem as ApiFileSystem
import li.cil.oc.api.fs.Handle
import li.cil.oc.api.fs.Mode
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.nbt.NBTTagCompound
import java.io.FileNotFoundException
import java.util.concurrent.Callable

class CompositeReadOnlyFileSystem(
    factories: LinkedHashMap<String, Callable<ApiFileSystem?>>
) : ApiFileSystem {
    var parts: LinkedHashMap<String, ApiFileSystem> = linkedMapOf()

    init {
        for ((name, factory) in factories) {
            val fs = factory.call()
            if (fs != null) {
                parts[name] = fs
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun isReadOnly() = true

    override fun spaceTotal(): Long = maxOf(spaceUsed(), parts.values.sumOf { it.spaceTotal() })

    override fun spaceUsed(): Long = parts.values.sumOf { it.spaceUsed() }

    // ----------------------------------------------------------------------- //

    override fun exists(path: String): Boolean = findFileSystem(path) != null

    override fun size(path: String): Long = findFileSystem(path)?.size(path) ?: 0L

    override fun isDirectory(path: String): Boolean = findFileSystem(path)?.isDirectory(path) ?: false

    override fun lastModified(path: String): Long = findFileSystem(path)?.lastModified(path) ?: 0L

    override fun list(path: String): Array<String>? {
        if (!isDirectory(path)) return null
        val result = mutableSetOf<String>()
        for (fs in parts.values) {
            if (fs.exists(path)) {
                try {
                    val l = fs.list(path)
                    if (l != null) {
                        for (e in l) {
                            val f = e.removeSuffix("/")
                            val d = "$f/"
                            // Avoid duplicates and always only use the latest entry.
                            result.remove(f)
                            result.remove(d)
                            result.add(e)
                        }
                    }
                } catch (_: Throwable) {
                }
            }
        }
        return result.toTypedArray()
    }

    // ----------------------------------------------------------------------- //

    override fun delete(path: String) = false

    override fun makeDirectory(path: String) = false

    override fun rename(from: String, to: String) = false

    override fun setLastModified(path: String, time: Long) = false

    // ----------------------------------------------------------------------- //

    override fun open(path: String, mode: Mode): Int {
        val fs = findFileSystem(path)
        return fs?.open(path, mode) ?: throw FileNotFoundException(path)
    }

    override fun getHandle(handle: Int): Handle? =
        parts.values.asSequence().map { it.getHandle(handle) }.firstOrNull { it != null }

    override fun close() {
        for (fs in parts.values) {
            fs.close()
        }
    }

    // ----------------------------------------------------------------------- //

    override fun load(nbt: NBTTagCompound) {
        for ((name, fs) in parts) {
            fs.load(nbt.getCompoundTag(name))
        }
    }

    override fun save(nbt: NBTTagCompound) {
        for ((name, fs) in parts) {
            nbt.setNewCompoundTag(name) { fs.save(it) }
        }
    }

    // ----------------------------------------------------------------------- //

    protected fun findFileSystem(path: String): ApiFileSystem? =
        parts.values.reversed().firstOrNull { it.exists(path) }
}
