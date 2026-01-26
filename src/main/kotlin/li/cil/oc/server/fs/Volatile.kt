package li.cil.oc.server.fs

internal abstract class Volatile : VirtualFileSystem() {
    override fun close() {
        super.close()
        root.children.clear()
    }
}
