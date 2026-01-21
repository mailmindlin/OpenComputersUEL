package li.cil.oc.server.fs

import li.cil.oc.OpenComputers
import li.cil.oc.api.fs.Mode
import li.cil.oc.util.SafeThreadPool
import li.cil.oc.util.ThreadPoolFactory
import net.minecraft.nbt.NBTTagCompound
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object BufferedCompanion {
    val fileSaveHandler: SafeThreadPool = ThreadPoolFactory.createSafePool("FileSystem", 1)
}

interface Buffered : OutputStreamFileSystem {
    val fileRoot: File

    val deletions: MutableMap<String, Long>

    var saving: Future<*>?

    // ----------------------------------------------------------------------- //

    override fun delete(path: String): Boolean {
        return if (super.delete(path)) {
            deletions[path] = System.currentTimeMillis()
            true
        } else {
            false
        }
    }

    override fun rename(from: String, to: String): Boolean {
        return if (super.rename(from, to)) {
            deletions[from] = System.currentTimeMillis()
            true
        } else {
            false
        }
    }

    // ----------------------------------------------------------------------- //

    override fun load(nbt: NBTTagCompound) {
        saving?.let { f ->
            try {
                f.get(120L, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                OpenComputers.log.warn("Waiting for filesystem to save took two minutes! Aborting.")
            } catch (e: CancellationException) {
                // NO-OP
            }
        }
        loadFiles(nbt)
        super.load(nbt)
    }

    fun loadFiles(nbt: NBTTagCompound) {
        synchronized(this) {
            fun recurse(path: String, directory: File) {
                makeDirectory(path)
                val files = directory.listFiles() ?: return
                for (child in files) {
                    if (!FileSystem.isValidFilename(child.name)) continue
                    val childPath = path + child.name
                    val childFile = File(directory, child.name)
                    if (child.exists() && child.isDirectory && child.list() != null) {
                        recurse("$childPath/", childFile)
                    } else if (!exists(childPath) || !isDirectory(childPath)) {
                        val stream = openOutputHandle(0, childPath, Mode.Write)
                        if (stream != null) {
                            try {
                                val input = FileInputStream(childFile)
                                val buffer = ByteArray(8 * 1024)
                                var read: Int
                                do {
                                    read = input.read(buffer)
                                    if (read > 0) {
                                        if (read == buffer.size) {
                                            stream.write(buffer)
                                        } else {
                                            stream.write(buffer.copyOfRange(0, read))
                                        }
                                    }
                                } while (read >= 0)
                                input.close()
                            } catch (_: FileNotFoundException) {
                                // File got deleted in the meantime.
                            }
                            stream.close()
                            setLastModified(childPath, childFile.lastModified())
                        }
                        // else: File is open for writing.
                    }
                }
                setLastModified(path, directory.lastModified())
            }
            val fileList = fileRoot.list()
            if (fileList == null || fileList.isEmpty()) {
                fileRoot.delete()
            } else {
                recurse("", fileRoot)
            }
        }
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        saving = BufferedCompanion.fileSaveHandler.withPool { pool ->
            pool.submit { saveFiles() }
        }
    }

    fun saveFiles() {
        synchronized(this) {
            for ((path, time) in deletions) {
                val file = File(fileRoot, path)
                if (FileUtils.isFileOlder(file, time))
                    FileUtils.deleteQuietly(file)
            }
            deletions.clear()

            val buffer = ByteBuffer.allocateDirect(16 * 1024)
            fun recurse(path: String): Boolean {
                val directory = File(fileRoot, path)
                directory.mkdirs()
                var dirChanged = false
                val children = list(path) ?: return false
                for (child in children) {
                    val childPath = path + child
                    if (isDirectory(childPath)) {
                        dirChanged = recurse(childPath) || dirChanged
                    } else {
                        val childFile = File(fileRoot, childPath)
                        val time = lastModified(childPath)
                        if (time == 0L || !childFile.exists() || FileUtils.isFileOlder(childFile, time)) {
                            FileUtils.deleteQuietly(childFile)
                            childFile.createNewFile()
                            val out = FileOutputStream(childFile).channel
                            val inputChannel = openInputChannel(childPath)!!

                            buffer.clear()
                            while (inputChannel.read(buffer) != -1) {
                                buffer.flip()
                                out.write(buffer)
                                buffer.compact()
                            }

                            buffer.flip()
                            while (buffer.hasRemaining()) {
                                out.write(buffer)
                            }

                            out.close()
                            inputChannel.close()
                            childFile.setLastModified(time)
                            dirChanged = true
                        }
                    }
                }
                if (dirChanged) {
                    directory.setLastModified(lastModified(path))
                    return true
                }
                return false
            }
            val rootList = list("")
            if (rootList == null || rootList.isEmpty()) {
                fileRoot.delete()
            } else {
                recurse("")
            }
        }
    }
}
