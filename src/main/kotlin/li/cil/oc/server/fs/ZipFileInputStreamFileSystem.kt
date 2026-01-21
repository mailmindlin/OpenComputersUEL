package li.cil.oc.server.fs

import com.google.common.cache.CacheBuilder
import li.cil.oc.OpenComputers
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.Callable
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ZipFileInputStreamFileSystem private constructor(
    private val archive: ArchiveDirectory
) : InputStreamFileSystem {
    override val inputHandles = mutableMapOf<Int, InputStreamFileSystem.InputHandle>()

    override fun spaceTotal() = spaceUsed()

    override fun spaceUsed() = spaceUsedLazy

    private val spaceUsedLazy: Long by lazy {
        fun recurse(d: ArchiveDirectory): Long = d.children.fold(0L) { acc, c ->
            acc + when (c) {
                is ArchiveDirectory -> recurse(c)
                is ArchiveFile -> c.size.toLong()
                else -> 0L
            }
        }
        synchronized(ZipFileInputStreamFileSystem) {
            recurse(archive)
        }
    }

    // ----------------------------------------------------------------------- //

    override fun exists(path: String): Boolean = synchronized(ZipFileInputStreamFileSystem) {
        entry(path) != null
    }

    override fun size(path: String): Long = synchronized(ZipFileInputStreamFileSystem) {
        val e = entry(path)
        if (e != null && !e.isDirectory) e.size.toLong() else 0L
    }

    override fun isDirectory(path: String): Boolean = synchronized(ZipFileInputStreamFileSystem) {
        entry(path)?.isDirectory ?: false
    }

    override fun lastModified(path: String): Long = synchronized(ZipFileInputStreamFileSystem) {
        entry(path)?.lastModified ?: 0L
    }

    override fun list(path: String): Array<String>? = synchronized(ZipFileInputStreamFileSystem) {
        val e = entry(path)
        if (e != null && e.isDirectory) e.list() else null
    }

    // ----------------------------------------------------------------------- //

    override fun openInputChannel(path: String): InputStreamFileSystem.InputChannel? =
        synchronized(ZipFileInputStreamFileSystem) {
            entry(path)?.let { e ->
                val stream = e.openStream()
                if (stream != null) InputStreamFileSystem.InputStreamChannel(stream) else null
            }
        }

    // ----------------------------------------------------------------------- //

    private fun entry(path: String): Archive? {
        val cleanPath = "/" + path.replace("\\", "/").replace("//", "/").removePrefix("/").removeSuffix("/")
        return if (cleanPath == "/") archive
        else archive.find(cleanPath.split("/"))
    }

    companion object {
        private val cache = CacheBuilder.newBuilder()
            .weakValues()
            .build<String, ArchiveDirectory>()

        @JvmStatic
        @Synchronized
        fun fromFile(file: File, innerPath: String): ZipFileInputStreamFileSystem? {
            return try {
                val archiveDir = cache.get("${file.path}:$innerPath", Callable {
                    val zip = ZipFile(file.path)
                    try {
                        val cleanedPath = innerPath.removePrefix("/").removeSuffix("/") + "/"
                        val rootEntry = zip.getEntry(cleanedPath)
                        if (rootEntry == null || !rootEntry.isDirectory) {
                            throw IllegalArgumentException("Root path $innerPath doesn't exist or is not a directory in ZIP file ${file.name}.")
                        }
                        val directories = mutableSetOf<ArchiveDirectory>()
                        val files = mutableSetOf<ArchiveFile>()
                        val iterator = zip.entries()
                        while (iterator.hasMoreElements()) {
                            val entry = iterator.nextElement()
                            if (entry.name.startsWith(cleanedPath)) {
                                if (entry.isDirectory) {
                                    directories.add(ArchiveDirectory(entry, cleanedPath))
                                } else {
                                    files.add(ArchiveFile(zip, entry, cleanedPath))
                                }
                            }
                        }
                        var root: ArchiveDirectory? = null
                        for (entry in directories + files) {
                            if (entry.path.isNotEmpty()) {
                                val parent = entry.path.substring(0, maxOf(entry.path.lastIndexOf('/'), 0))
                                directories.find { d -> d.path == parent }?.children?.add(entry)
                            } else {
                                assert(entry is ArchiveDirectory)
                                root = entry as ArchiveDirectory
                            }
                        }
                        root
                    } finally {
                        zip.close()
                    }
                })
                if (archiveDir != null) ZipFileInputStreamFileSystem(archiveDir) else null
            } catch (e: Throwable) {
                OpenComputers.log.warn("Failed creating ZIP file system.", e)
                null
            }
        }
    }

    abstract class Archive(entry: ZipEntry, root: String) {
        val path: String = entry.name.removePrefix(root).removeSuffix("/")

        val name: String = path.substring(path.lastIndexOf('/') + 1)

        val lastModified: Long = entry.time

        val isDirectory: Boolean = entry.isDirectory

        abstract val size: Int

        abstract fun list(): Array<String>?

        abstract fun openStream(): InputStream?

        abstract fun find(path: List<String>): Archive?
    }

    private class ArchiveFile(zip: ZipFile, entry: ZipEntry, root: String) : Archive(entry, root) {
        val data: ByteArray = run {
            val inputStream = zip.getInputStream(entry)
            inputStream.readBytes()
        }

        override val size: Int = data.size

        override fun list(): Array<String>? = null

        override fun openStream(): InputStream = ByteArrayInputStream(data)

        override fun find(path: List<String>): Archive? =
            if (path.size == 1 && path.first() == name) this else null
    }

    class ArchiveDirectory(entry: ZipEntry, root: String) : Archive(entry, root) {
        val children = mutableSetOf<Archive>()

        override val size = 0

        override fun list(): Array<String> = children.map { c ->
            c.name + if (c.isDirectory) "/" else ""
        }.toTypedArray()

        override fun openStream(): InputStream? = null

        override fun find(path: List<String>): Archive? {
            if (path.isEmpty()) return null
            return if (path.first() == name) {
                if (path.size == 1) this
                else {
                    val subPath = path.drop(1)
                    children.asSequence().mapNotNull { it.find(subPath) }.firstOrNull()
                }
            } else null
        }
    }
}
