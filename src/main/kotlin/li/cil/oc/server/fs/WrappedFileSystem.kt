package li.cil.oc.server.fs

import li.cil.oc.api.fs.FileSystem
import li.cil.oc.api.fs.Mode
import java.nio.ByteBuffer

internal open class WrappedFileSystem<F: FileSystem>(protected val inner: F): OutputStreamFileSystem(), FileSystem by inner {
    override fun openOutputHandle(id: Int, path: String, mode: Mode): OutputHandle? {
        TODO("Not yet implemented")
    }

    override fun openInputChannel(path: String): InputChannel? {
        TODO("Not yet implemented")
    }

    protected sealed class WrappedInputChannel(val inner: InputChannel): InputChannel {
        override fun isOpen(): Boolean = inner.isOpen

        override fun close() = inner.close()
        override fun position(): Long = inner.position()
        override fun position(newPosition: Long): Long = inner.position(newPosition)
        override fun read(dst: ByteArray, off: Int, len: Int): Int = inner.read(dst, off, len)
        override fun read(dst: ByteBuffer): Int = inner.read(dst)
    }

    protected class WrappedOutputHandle(val inner: OutputHandle): OutputHandle(inner.owner, inner.handle, inner.path) {
        override fun position(): Long = inner.position()
        override fun length(): Long = inner.length()
        override fun write(value: ByteArray) = inner.write(value)
        override fun close() = inner.close()
        override fun read(into: ByteArray): Int = inner.read(into)
        override val isClosed: Boolean
            get() = inner.isClosed
        override fun seek(to: Long): Long = inner.seek(to)
        override val owner: OutputStreamFileSystem
            get() = inner.owner
    }
}