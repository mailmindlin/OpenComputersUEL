package li.cil.oc.integration.computercraft

import java.io.IOException
import java.io.OutputStream

import dan200.computercraft.api.filesystem.IWritableMount
import li.cil.oc.api.fs.Mode
import li.cil.oc.server.fs.OutputStreamFileSystem

internal class ComputerCraftWritableFileSystem(private val mount: IWritableMount) : OutputStreamFileSystem() {
    override fun delete(path: String): Boolean = try {
        mount.delete(path)
        true
    } catch (t: Throwable) {
        false
    }

    override fun makeDirectory(path: String): Boolean = try {
        mount.makeDirectory(path)
        true
    } catch (t: Throwable) {
        false
    }

    override fun openOutputHandle(id: Int, path: String, mode: Mode): OutputStreamFileSystem.OutputHandle? = try {
        val stream = when (mode) {
            Mode.Append -> mount.openForAppend(path)
            Mode.Write -> mount.openForWrite(path)
            else -> throw IllegalArgumentException()
        }
        ComputerCraftOutputHandle(mount, stream, this, id, path)
    } catch (t: Exception) {
        null
    }

    protected inner class ComputerCraftOutputHandle(
        val mount: IWritableMount,
        val stream: OutputStream,
        owner: OutputStreamFileSystem,
        handle: Int,
        path: String
    ) : OutputStreamFileSystem.OutputHandle(owner, handle, path) {
        override fun length(): Long = mount.getSize(path)

        override fun position(): Long = throw IOException("bad file descriptor")

        override fun write(value: ByteArray) {
            stream.write(value)
        }
    }

    override fun spaceTotal() = 0L

    override fun spaceUsed() = 0L

    // ----------------------------------------------------------------------- //

    override fun exists(path: String): Boolean = mount.exists(path)

    override fun isDirectory(path: String): Boolean = mount.isDirectory(path)

    override fun lastModified(path: String) = 0L

    override fun list(path: String): Array<String> {
        val result = java.util.ArrayList<String>()
        mount.list(path, result)
        return result.toTypedArray()
    }

    override fun size(path: String): Long = mount.getSize(path)

    // ----------------------------------------------------------------------- //

    override fun openInputChannel(path: String) = try {
        InputStreamChannel(mount.openForRead(path))
    } catch (t: Exception) {
        null
    }
}
