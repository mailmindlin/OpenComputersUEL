package li.cil.oc.server.fs

import java.io.FileNotFoundException

import li.cil.oc.api.fs.FileSystem
import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound

internal class ReadOnlyWrapper(private val fileSystem: FileSystem): FileSystem {
  override fun isReadOnly() = true

  override fun spaceTotal() = fileSystem.spaceUsed()

  override fun spaceUsed() = fileSystem.spaceUsed()

  override fun exists(path: String) = fileSystem.exists(path)

  override fun size(path: String) = fileSystem.size(path)

  override fun isDirectory(path: String) = fileSystem.isDirectory(path)

  override fun lastModified(path: String) = fileSystem.lastModified(path)

  override fun list(path: String) = fileSystem.list(path)

  override fun delete(path: String) = false

  override fun makeDirectory(path: String) = false

  override fun rename(from: String, to: String) = false

  override fun setLastModified(path: String, time: Long) = false

  override fun open(path: String, mode: Mode) = when (mode) {
    Mode.Read -> fileSystem.open(path, mode)
    Mode.Write -> throw FileNotFoundException("read-only filesystem; cannot open for writing: $path")
    Mode.Append -> throw FileNotFoundException("read-only filesystem; cannot open for appending: $path")
  }

  override fun getHandle(handle: Int) = fileSystem.getHandle(handle)

  override fun close() = fileSystem.close()

  override fun load(nbt: NBTTagCompound) = fileSystem.load(nbt)

  override fun save(nbt: NBTTagCompound) = fileSystem.save(nbt)
}
