package li.cil.oc.server.fs

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.detail.FileSystemAPI
import li.cil.oc.api.fs.Label
import li.cil.oc.api.fs.Mode
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.traits.FileSystemLike
import li.cil.oc.server.component.FileSystem as FileSystemComponent
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.DimensionManager
import java.io.File
import java.io.IOException
import java.net.MalformedURLException
import java.net.URISyntaxException
import java.net.URL
import java.util.UUID
import li.cil.oc.api.fs.FileSystem as ApiFileSystem

object FileSystem : FileSystemAPI {
    private val isCaseInsensitive: Boolean by lazy {
        Settings.get.forceCaseInsensitive || run {
            try {
                val uuid = UUID.randomUUID().toString()
                val lowerCase = File(DimensionManager.getCurrentSaveRootDirectory(), "${uuid}oc_rox")
                val upperCase = File(DimensionManager.getCurrentSaveRootDirectory(), "${uuid}OC_ROX")
                // This should NEVER happen but could also lead to VERY weird bugs, so we
                // make sure the files don't exist.
                if (lowerCase.exists()) lowerCase.delete()
                if (upperCase.exists()) upperCase.delete()
                lowerCase.createNewFile()
                val insensitive = upperCase.exists()
                lowerCase.delete()
                insensitive
            } catch (t: Throwable) {
                // Among the security errors, createNewFile can throw an IOException.
                // We just fall back to assuming case insensitive, since that's always
                // safe in those cases.
                OpenComputers.log.warn("Couldn't determine if file system is case sensitive, falling back to insensitive.", t)
                true
            }
        }
    }

    // Worst-case: we're on Windows or using a FAT32 partition mounted in *nix.
    // Note: we allow / as the path separator and expect all \s to be converted
    // accordingly before the path is passed to the file system.
    private val invalidChars = setOf('\\', ':', '*', '?', '"', '<', '>', '|')

    @JvmStatic
    fun isValidFilename(name: String): Boolean = name.none { invalidChars.contains(it) }

    @JvmStatic
    fun validatePath(path: String): String {
        if (!isValidFilename(path)) {
            throw IOException("path contains invalid characters")
        }
        return path
    }

    override fun fromClass(clazz: Class<*>, domain: String, root: String): ApiFileSystem? {
        val innerPath = ("/assets/$domain/${root.trim()}/").replace("//", "/")

        val codeSource = clazz.protectionDomain.codeSource.location.path
        val (codeUrl, isArchive) = if (codeSource.contains(".zip!") || codeSource.contains(".jar!")) {
            codeSource.substring(0, codeSource.lastIndexOf('!')) to true
        } else {
            codeSource to false
        }

        val url = try {
            URL(codeUrl)
        } catch (_: MalformedURLException) {
            try {
                URL("file://$codeUrl")
            } catch (_: MalformedURLException) {
                null
            }
        }

        val file = if (url != null) {
            try {
                File(url.toURI())
            } catch (_: URISyntaxException) {
                File(url.path)
            }
        } else {
            File(codeSource)
        }

        return if (isArchive) {
            ZipFileInputStreamFileSystem.fromFile(file, innerPath.substring(1))
        } else {
            if (!file.exists() || file.isDirectory) return null
            val fsp = File(File(file.parent), innerPath)
            if (fsp.exists() && fsp.isDirectory) {
                ReadOnlyFileSystem(fsp)
            } else {
                val classpath = System.getProperty("java.class.path").split(System.getProperty("path.separator"))
                val found = classpath.find { cp ->
                    val fspCp = File(File(cp), innerPath)
                    fspCp.exists() && fspCp.isDirectory
                }
                if (found != null) {
                    ReadOnlyFileSystem(File(File(found), innerPath))
                } else {
                    null
                }
            }
        }
    }

    override fun fromSaveDirectory(root: String, capacity: Long, buffered: Boolean): ApiFileSystem? {
        val path = File(DimensionManager.getCurrentSaveRootDirectory(), Settings.savePath + root)
        if (!path.isDirectory)
            path.delete()
        path.mkdirs()
        if (!path.exists() || !path.isDirectory) return null

        val inner = if (buffered) Buffered(BufferedFileSystem(), path)
            else ReadWriteFileSystem(path)
        return Capacity(inner, capacity)
    }

    @JvmStatic
    fun removeAddress(fsStack: ItemStack): Boolean {
        val subItem = Delegator.subItem(fsStack)
        if (subItem is FileSystemLike) {
            val data = li.cil.oc.integration.opencomputers.Item.dataTag(fsStack)
            if (data.hasKey("node")) {
                val nodeData = data.getCompoundTag("node")
                if (nodeData.hasKey("address")) {
                    nodeData.removeTag("address")
                    return true
                }
            }
        }
        return false
    }

    override fun fromMemory(capacity: Long): ApiFileSystem = Capacity(RamFileSystem(), capacity)

    override fun asReadOnly(fileSystem: ApiFileSystem): ApiFileSystem =
        if (fileSystem.isReadOnly) fileSystem
        else ReadOnlyWrapper(fileSystem)

    override fun asManagedEnvironment(
        fileSystem: ApiFileSystem?,
        label: Label?,
        host: EnvironmentHost?,
        accessSound: String?,
        speed: Int
    ): FileSystemComponent? {
        return fileSystem?.let { fs ->
            FileSystemComponent(fs, label, host, accessSound, (speed - 1).coerceIn(0, 5))
        }
    }

    override fun asManagedEnvironment(
        fileSystem: ApiFileSystem?,
        label: String?,
        host: EnvironmentHost?,
        accessSound: String?,
        speed: Int
    ): FileSystemComponent? =
        asManagedEnvironment(fileSystem, label?.let { ReadOnlyLabel(it) }, host, accessSound, speed)

    override fun asManagedEnvironment(fileSystem: ApiFileSystem?): FileSystemComponent? =
        asManagedEnvironment(fileSystem, null as Label?, null, null, 1)

    override fun asManagedEnvironment(
        fileSystem: ApiFileSystem?,
        label: Label?,
        host: EnvironmentHost?,
        accessSound: String?
    ): FileSystemComponent? =
        asManagedEnvironment(fileSystem, label, host, accessSound, 1)

    override fun asManagedEnvironment(
        fileSystem: ApiFileSystem?,
        label: String?,
        host: EnvironmentHost?,
        accessSound: String?
    ): FileSystemComponent? =
        asManagedEnvironment(fileSystem, label?.let { ReadOnlyLabel(it) }, host, accessSound, 1)

    override fun asManagedEnvironment(
        fileSystem: ApiFileSystem?,
        label: Label?
    ): FileSystemComponent? =
        asManagedEnvironment(fileSystem, label, null, null, 1)

    override fun asManagedEnvironment(
        fileSystem: ApiFileSystem?,
        label: String?
    ): FileSystemComponent? =
        asManagedEnvironment(fileSystem, label?.let { ReadOnlyLabel(it) }, null, null, 1)

    abstract class ItemLabel(val stack: ItemStack) : Label

    class ReadOnlyLabel(private val label: String?) : Label {
        override fun setLabel(value: String?) {
            throw IllegalArgumentException("label is read only")
        }

        override fun getLabel() = label

        private val LabelTag = Settings.namespace + "fs.label"

        override fun load(nbt: NBTTagCompound) {}

        override fun save(nbt: NBTTagCompound) {
            if (label != null) {
                nbt.setString(LabelTag, label)
            }
        }
    }

    private class ReadOnlyFileSystem(override val root: File) : FileInputStreamFileSystem()

    private class ReadWriteFileSystem(override val root: File) : FileOutputStreamFileSystem()

    private class RamFileSystem() : Volatile() {
        override fun spaceTotal(): Long = -1L
        override fun spaceUsed(): Long = -1L
    }

    private class BufferedFileSystem(): VirtualFileSystem() {
        override fun segments(path: String): List<String> {
            val parts = FileSystem.validatePath(path).split("/").filter { it.isNotEmpty() }
            return if (isCaseInsensitive) toCaseInsensitive(parts) else parts
        }

        private fun toCaseInsensitive(path: List<String>): List<String> {
            var node: VirtualFileSystem.VirtualObject? = root
            return path.map { segment ->
                assert(node != null) { "corrupted virtual file system" }
                val dir = node as? VirtualDirectory ?: return@map segment
                val match = dir.children.entries
                    .find { (key, _) -> key.equals(segment, ignoreCase = true) }
                    ?: return@map segment

                val (name, child) = match
                node = child as? VirtualDirectory
                name
            }
        }
    }
}
