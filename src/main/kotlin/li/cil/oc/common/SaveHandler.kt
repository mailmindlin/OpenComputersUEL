package li.cil.oc.common

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.SafeThreadPool
import li.cil.oc.util.ThreadPoolFactory
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.ChunkPos
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.commons.lang3.JavaVersion
import org.apache.commons.lang3.SystemUtils
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileFilter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

// Used by the native lua state to store kernel and stack data in auxiliary
// files instead of directly in the tile entity data, avoiding potential
// problems with the tile entity data becoming too large.
object SaveHandler {
    private val uuidRegex = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")

    private const val TimeToHoldOntoOldSaves = 60 * 1000L

    // THIS IS A MASSIVE HACK OF THE UGLIEST KINDS.
    // But it works, and the alternative would be to change the Persistable
    // interface to pass along this state to *everything that gets saved ever*,
    // which in 99% of the cases it doesn't need to know. So yes, this is fugly,
    // but the "clean" solution would be no less fugly.
    // Why is this even required? To avoid flushing file systems to disk and
    // avoid persisting machine states when sending description packets to clients,
    // which takes a lot of time and is completely unnecessary in those cases.
    @JvmField
    var savingForClients = false

    class SaveDataEntry(val data: ByteArray, val pos: ChunkPos, val name: String, val dimension: Int) : Runnable {
        override fun run() {
            val path = statePath
            val dimPath = File(path, dimension.toString())
            val chunkPath = File(dimPath, "${pos.x}.${pos.z}")
            chunkDirs.add(chunkPath)
            if (!chunkPath.exists()) {
                chunkPath.mkdirs()
            }
            val file = File(chunkPath, name)
            try {
                val fos = BufferedOutputStream(FileOutputStream(file))
                fos.write(data)
                fos.close()
            } catch (e: IOException) {
                OpenComputers.log.warn("Error saving auxiliary tile entity data to '${file.absolutePath}.", e)
            }
        }
    }

    @JvmField
    val stateSaveHandler: SafeThreadPool = ThreadPoolFactory.createSafePool("SaveHandler", 1)

    @JvmField
    val chunkDirs = ConcurrentLinkedDeque<File>()

    private val saving = mutableMapOf<String, Future<*>>()

    @JvmStatic
    val savePath: File get() = File(DimensionManager.getCurrentSaveRootDirectory(), Settings.savePath)

    @JvmStatic
    val statePath: File get() = File(savePath, "state")

    @JvmStatic
    fun scheduleSave(host: MachineHost, nbt: NBTTagCompound, name: String, data: ByteArray) {
        scheduleSave(BlockPosition(host), nbt, name, data)
    }

    @JvmStatic
    fun scheduleSave(host: MachineHost, nbt: NBTTagCompound, name: String, save: (NBTTagCompound) -> Unit) {
        scheduleSave(host, nbt, name, writeNBT(save))
    }

    @JvmStatic
    fun scheduleSave(host: EnvironmentHost, nbt: NBTTagCompound, name: String, save: (NBTTagCompound) -> Unit) {
        scheduleSave(BlockPosition(host), nbt, name, writeNBT(save))
    }

    @JvmStatic
    fun scheduleSave(world: World, x: Double, z: Double, nbt: NBTTagCompound, name: String, data: ByteArray) {
        scheduleSave(BlockPosition(x, 0.0, z, world), nbt, name, data)
    }

    @JvmStatic
    fun scheduleSave(world: World, x: Double, z: Double, nbt: NBTTagCompound, name: String, save: (NBTTagCompound) -> Unit) {
        scheduleSave(world, x, z, nbt, name, writeNBT(save))
    }

    @JvmStatic
    fun scheduleSave(position: BlockPosition, nbt: NBTTagCompound, name: String, data: ByteArray) {
        val world = position.world ?: return
        // Try to exclude wrapped/client-side worlds.
        if (world is WorldServer) {
            val dimension = world.provider.dimension
            val chunk = ChunkPos(position.x shr 4, position.z shr 4)

            // We have to save the dimension and chunk coordinates, because they are
            // not available on load / may have changed if the computer was moved.
            nbt.setInteger("dimension", dimension)
            nbt.setInteger("chunkX", chunk.x)
            nbt.setInteger("chunkZ", chunk.z)

            scheduleSave(dimension, chunk, name, data)
        }
    }

    private fun writeNBT(save: (NBTTagCompound) -> Unit): ByteArray {
        val tmpNbt = NBTTagCompound()
        save(tmpNbt)
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        CompressedStreamTools.write(tmpNbt, dos)
        return baos.toByteArray()
    }

    @JvmStatic
    fun loadNBT(nbt: NBTTagCompound, name: String): NBTTagCompound {
        val data = load(nbt, name)
        return if (data.isNotEmpty()) {
            try {
                val bais = ByteArrayInputStream(data)
                val dis = DataInputStream(bais)
                CompressedStreamTools.read(dis)
            } catch (t: Throwable) {
                OpenComputers.log.warn("There was an error trying to restore a block's state from external data. This indicates that data was somehow corrupted.", t)
                NBTTagCompound()
            }
        } else {
            NBTTagCompound()
        }
    }

    @JvmStatic
    fun load(nbt: NBTTagCompound, name: String): ByteArray {
        // Since we have no world yet, we rely on the dimension we were saved in.
        // Same goes for the chunk. This also works around issues with computers
        // being moved (e.g. Redstone in Motion).
        val dimension = nbt.getInteger("dimension")
        val chunk = ChunkPos(nbt.getInteger("chunkX"), nbt.getInteger("chunkZ"))

        // Wait for the latest save task for the requested file to complete.
        // This prevents the chance of loading an outdated version
        // of this file.
        saving[name]?.let { f ->
            try {
                f.get(120L, TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                OpenComputers.log.warn("Waiting for state data to save took two minutes! Aborting.")
            } catch (e: CancellationException) {
                // NO-OP
            }
        }
        saving.remove(name)

        return load(dimension, chunk, name)
    }

    @JvmStatic
    fun scheduleSave(dimension: Int, chunk: ChunkPos?, name: String, data: ByteArray) {
        if (chunk == null) throw IllegalArgumentException("chunk is null")
        // Disregarding whether or not there already was a
        // save submitted for the requested file
        // allows for better concurrency at the cost of
        // doing more writing operations.
        stateSaveHandler.withPool { pool ->
            pool.submit(SaveDataEntry(data, chunk, name, dimension))
        }?.let { saving[name] = it }
    }

    @JvmStatic
    fun load(dimension: Int, chunk: ChunkPos?, name: String): ByteArray {
        if (chunk == null) throw IllegalArgumentException("chunk is null")

        val path = statePath
        val dimPath = File(path, dimension.toString())
        val chunkPath = File(dimPath, "${chunk.x}.${chunk.z}")
        val file = File(chunkPath, name)
        if (!file.exists()) return ByteArray(0)
        return try {
            val bis = BufferedInputStream(FileInputStream(file))
            val bos = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var read: Int
            do {
                read = bis.read(buffer)
                if (read > 0) {
                    bos.write(buffer, 0, read)
                }
            } while (read >= 0)
            bis.close()
            bos.toByteArray()
        } catch (e: IOException) {
            OpenComputers.log.warn("Error loading auxiliary tile entity data.", e)
            ByteArray(0)
        }
    }

    @JvmStatic
    fun cleanSaveData() {
        // Delete empty folders to keep the state folder clean.
        val emptyDirs = savePath.listFiles(FileFilter { file ->
            file.isDirectory &&
                // Make sure we only consider file system folders (UUID).
                uuidRegex.matches(file.name) &&
                // We set the modified time in the save() method of unbuffered file
                // systems, to avoid deleting in-use folders here.
                System.currentTimeMillis() - file.lastModified() > TimeToHoldOntoOldSaves &&
                (file.list()?.isEmpty() ?: true)
        })
        emptyDirs?.filterNotNull()?.forEach { it.delete() }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @Suppress("unused")
    fun onWorldLoad(e: WorldEvent.Load) {
        if (!e.world.isRemote) {
            // Touch all externally saved data when loading, to avoid it getting
            // deleted in the next save (because the now - save time will usually
            // be larger than the time out after loading a world again).
            if (SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_1_7)) {
                SaveHandlerJava17Functionality.visitJava17(statePath)
            } else {
                visitJava16()
            }
        }
    }

    private fun visitJava16() {
        // This may run into infinite loops if there are evil symlinks.
        // But that's really not something I'm bothered by, it's a fallback.
        fun recurse(file: File) {
            file.setLastModified(System.currentTimeMillis())
            if (file.exists() && file.isDirectory && file.list() != null) {
                file.listFiles()?.forEach { recurse(it) }
            }
        }
        recurse(statePath)
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    @Suppress("unused")
    fun onWorldSave(e: WorldEvent.Save) {
        stateSaveHandler.withPool { pool ->
            pool.submit { cleanSaveData() }
        }
    }
}

object SaveHandlerJava17Functionality {
    @JvmStatic
    fun visitJava17(statePath: File) {
        Files.walkFileTree(statePath.toPath(), object : FileVisitor<Path> {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                file.toFile().setLastModified(System.currentTimeMillis())
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult = FileVisitResult.CONTINUE

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult = FileVisitResult.CONTINUE

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult = FileVisitResult.CONTINUE
        })
    }
}
