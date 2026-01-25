package li.cil.oc.integration.computercraft

import java.io.IOException
import java.io.OutputStream

import dan200.computercraft.api.filesystem.IWritableMount
import li.cil.oc.api.fs.Mode
import li.cil.oc.server.fs.OutputStreamFileSystem

class ComputerCraftWritableFileSystem(override val mount: IWritableMount) :
    ComputerCraftFileSystem(mount), OutputStreamFileSystem {

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

    protected override fun openOutputHandle(id: Int, path: String, mode: Mode): OutputStreamFileSystem.OutputHandle? = try {
        val stream = when (mode) {
            Mode.Append -> mount.openForAppend(path)
            Mode.Write -> mount.openForWrite(path)
            else -> throw IllegalArgumentException()
        }
        ComputerCraftOutputHandle(mount, stream, this, id, path)
    } catch (t: Throwable) {
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
}
