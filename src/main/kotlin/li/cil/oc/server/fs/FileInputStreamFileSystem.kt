package li.cil.oc.server.fs

import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

interface FileInputStreamFileSystem : InputStreamFileSystem {
    val root: File

    // ----------------------------------------------------------------------- //

    override fun spaceTotal() = spaceUsed()

    override fun spaceUsed(): Long

    // ----------------------------------------------------------------------- //

    override fun exists(path: String) = File(root, FileSystem.validatePath(path)).exists()

    override fun size(path: String): Long {
        val file = File(root, FileSystem.validatePath(path))
        return if (file.isFile) file.length() else 0L
    }

    override fun isDirectory(path: String) = File(root, FileSystem.validatePath(path)).isDirectory

    override fun lastModified(path: String) = File(root, FileSystem.validatePath(path)).lastModified()

    override fun list(path: String): Array<String> {
        val file = File(root, FileSystem.validatePath(path))
        return when {
            file.exists() && file.isFile -> arrayOf(file.name)
            file.exists() && file.isDirectory && file.list() != null ->
                file.listFiles()!!.map { f -> if (f.isDirectory) "${f.name}/" else f.name }.toTypedArray()
            else -> throw FileNotFoundException("no such file or directory: $path")
        }
    }

    // ----------------------------------------------------------------------- //

    override fun openInputChannel(path: String): InputStreamFileSystem.InputChannel? =
        FileChannel(File(root, path))

    class FileChannel(file: File) : InputStreamFileSystem.InputChannel {
        private val channel = RandomAccessFile(file, "r").channel

        override fun position(newPosition: Long): Long {
            channel.position(newPosition)
            return channel.position()
        }

        override fun position() = channel.position()

        override fun close() = channel.close()

        override fun isOpen() = channel.isOpen

        override fun read(dst: ByteArray) = channel.read(ByteBuffer.wrap(dst))

        override fun read(dst: ByteBuffer) = channel.read(dst)
    }

    companion object {
        fun computeSpaceUsed(root: File): Long {
            fun recurse(path: File): Long {
                return if (path.isDirectory) {
                    path.listFiles()?.fold(0L) { acc, f -> acc + recurse(f) } ?: 0L
                } else {
                    path.length()
                }
            }
            return recurse(root)
        }
    }
}
