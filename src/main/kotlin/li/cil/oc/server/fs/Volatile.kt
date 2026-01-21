package li.cil.oc.server.fs

interface Volatile : VirtualFileSystem {
    override fun close() {
        super.close()
        root.children.clear()
    }
}
