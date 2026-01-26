package li.cil.oc.server.fs

import li.cil.oc.api.fs.Mode
import li.cil.oc.server.fs.FileInputStreamFileSystem.FileChannel
import net.minecraft.nbt.NBTTagCompound
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

abstract class FileOutputStreamFileSystem : OutputStreamFileSystem() {
    protected abstract val root: File

    override fun spaceTotal() = -1L
    override fun spaceUsed() = -1L

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

    override fun delete(path: String): Boolean {
        val file = File(root, FileSystem.validatePath(path))
        return file == root || file.delete()
    }

    override fun makeDirectory(path: String) = File(root, FileSystem.validatePath(path)).mkdir()

    override fun rename(from: String, to: String): Boolean {
        return try {
            Files.move(
                File(root, FileSystem.validatePath(from)).toPath(),
                File(root, FileSystem.validatePath(to)).toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun setLastModified(path: String, time: Long) =
        File(root, FileSystem.validatePath(path)).setLastModified(time)

    // ----------------------------------------------------------------------- //

    override fun openInputChannel(path: String): InputChannel? =
        FileChannel(File(root, path))

    override fun openOutputHandle(id: Int, path: String, mode: Mode): OutputHandle? {
        val modeString = when (mode) {
            Mode.Append, Mode.Write -> "rw"
            else -> throw IllegalArgumentException()
        }
        return FileHandle(RandomAccessFile(File(root, path), modeString), this, id, path, mode)
    }

    // ----------------------------------------------------------------------- //

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        root.mkdirs()
        root.setLastModified(System.currentTimeMillis())
    }

    // ----------------------------------------------------------------------- //

    class FileHandle(
        val file: RandomAccessFile,
        owner: OutputStreamFileSystem,
        handle: Int,
        path: String,
        mode: Mode
    ) : OutputHandle(owner, handle, path) {
        init {
            if (mode == Mode.Write) {
                file.setLength(0)
            }
        }

        override fun position() = file.filePointer

        override fun length() = file.length()

        override fun close() {
            super.close()
            file.close()
        }

        override fun seek(to: Long): Long {
            file.seek(to)
            return to
        }

        override fun write(value: ByteArray) = file.write(value)
    }
}
