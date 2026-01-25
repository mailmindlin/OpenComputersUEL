package li.cil.oc.server.fs

import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

interface FileOutputStreamFileSystem : FileInputStreamFileSystem, OutputStreamFileSystem {
    override fun spaceTotal() = -1L

    override fun spaceUsed() = -1L

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

    override fun openOutputHandle(id: Int, path: String, mode: Mode): OutputStreamFileSystem.OutputHandle? {
        val modeString = when (mode) {
            Mode.Append, Mode.Write -> "rw"
            else -> throw IllegalArgumentException()
        }
        return FileHandle(RandomAccessFile(File(root, path), modeString), this, id, path, mode)
    }

    // ----------------------------------------------------------------------- //

    override fun save(nbt: NBTTagCompound) {
        super<OutputStreamFileSystem>.save(nbt)
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
    ) : OutputStreamFileSystem.OutputHandle(owner, handle, path) {
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
