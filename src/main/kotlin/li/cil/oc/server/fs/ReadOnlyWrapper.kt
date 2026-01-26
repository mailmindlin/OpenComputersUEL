package li.cil.oc.server.fs

import java.io.FileNotFoundException

import li.cil.oc.api.fs.FileSystem
import li.cil.oc.api.fs.Handle
import li.cil.oc.api.fs.Mode
import net.minecraft.nbt.NBTTagCompound

internal class ReadOnlyWrapper(private val fileSystem: FileSystem): FileSystem by fileSystem {
  override fun isReadOnly() = true

  override fun delete(path: String) = false

  override fun makeDirectory(path: String) = false

  override fun rename(from: String, to: String) = false

  override fun setLastModified(path: String, time: Long) = false

  override fun open(path: String, mode: Mode) = when (mode) {
    Mode.Read -> fileSystem.open(path, mode)
    Mode.Write -> throw FileNotFoundException("read-only filesystem; cannot open for writing: $path")
    Mode.Append -> throw FileNotFoundException("read-only filesystem; cannot open for appending: $path")
  }
}
