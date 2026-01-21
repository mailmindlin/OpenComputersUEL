package li.cil.oc.integration.computercraft

import dan200.computercraft.api.filesystem.IMount
import li.cil.oc.server.fs.InputStreamFileSystem

class ComputerCraftFileSystem(val mount: IMount) : InputStreamFileSystem() {
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

    protected override fun openInputChannel(path: String) = try {
        InputStreamChannel(mount.openForRead(path))
    } catch (t: Throwable) {
        null
    }
}
