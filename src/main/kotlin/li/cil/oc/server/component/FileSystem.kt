package li.cil.oc.server.component

import com.google.common.io.Files
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.fs.Label
import li.cil.oc.api.fs.Mode
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractValue
import li.cil.oc.common.SaveHandler
import li.cil.oc.util.setNewCompoundTag
import li.cil.oc.server.PacketSender as ServerPacketSender
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagIntArray
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants.NBT
import java.io.FileNotFoundException
import java.io.IOException
import li.cil.oc.api.fs.FileSystem as IFileSystem

class FileSystem(
    val fileSystem: IFileSystem,
    var label: Label?,
    val host: EnvironmentHost?,
    val sound: String?,
    val speed: Int
) : ManagedEnvironmentKt(), DeviceInfo {

    override val node = Network.newNode(this, Visibility.Network)!!
        .withComponent("filesystem", Visibility.Neighbors)
        .withConnector()
        .create()

    private val owners = mutableMapOf<String, MutableSet<Int>>()

    val readCosts = doubleArrayOf(1.0 / 1, 1.0 / 4, 1.0 / 7, 1.0 / 10, 1.0 / 13, 1.0 / 15)
    val seekCosts = doubleArrayOf(1.0 / 1, 1.0 / 4, 1.0 / 7, 1.0 / 10, 1.0 / 13, 1.0 / 15)
    val writeCosts = doubleArrayOf(1.0 / 1, 1.0 / 2, 1.0 / 3, 1.0 / 4, 1.0 / 5, 1.0 / 6)

    // ----------------------------------------------------------------------- //

    private val deviceInfo_ by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Volume,
            DeviceAttribute.Description to "Filesystem",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "MPFS.21.6",
            DeviceAttribute.Capacity to (fileSystem.spaceTotal() * 1.024).toInt().toString(),
            DeviceAttribute.Size to fileSystem.spaceTotal().toString(),
            DeviceAttribute.Clock to "${(2000 / readCosts[speed]).toInt() / 100}/${(2000 / seekCosts[speed]).toInt() / 100}/${(2000 / writeCosts[speed]).toInt() / 100}"
        )
    }

    override fun getDeviceInfo() = deviceInfo_

    // ----------------------------------------------------------------------- //

    @Callback(direct = true, doc = """function():string -- Get the current label of the drive.""")
    @Synchronized
    fun getLabel(context: Context, args: Arguments): Array<Any?>? {
        return if (label != null) result(label!!.label) else null
    }

    @Callback(doc = """function(value:string):string -- Sets the label of the drive. Returns the new value, which may be truncated.""")
    @Synchronized
    fun setLabel(context: Context, args: Arguments): Array<Any?> {
        if (label == null) throw Exception("drive does not support labeling")
        if (args.checkAny(0) == null) label!!.setLabel(null) else label!!.setLabel(args.checkString(0))
        return result(label!!.label)
    }

    @Callback(direct = true, doc = """function():boolean -- Returns whether the file system is read-only.""")
    @Synchronized
    fun isReadOnly(context: Context, args: Arguments): Array<Any?> = result(fileSystem.isReadOnly)

    @Callback(direct = true, doc = """function():number -- The overall capacity of the file system, in bytes.""")
    @Synchronized
    fun spaceTotal(context: Context, args: Arguments): Array<Any?> {
        val space = fileSystem.spaceTotal()
        return if (space < 0) result(Double.POSITIVE_INFINITY) else result(space)
    }

    @Callback(direct = true, doc = """function():number -- The currently used capacity of the file system, in bytes.""")
    @Synchronized
    fun spaceUsed(context: Context, args: Arguments): Array<Any?> = result(fileSystem.spaceUsed())

    @Callback(direct = true, doc = """function(path:string):boolean -- Returns whether an object exists at the specified absolute path in the file system.""")
    @Synchronized
    fun exists(context: Context, args: Arguments): Array<Any?> {
        diskActivity()
        return result(fileSystem.exists(clean(args.checkString(0))))
    }

    @Callback(direct = true, doc = """function(path:string):number -- Returns the size of the object at the specified absolute path in the file system.""")
    @Synchronized
    fun size(context: Context, args: Arguments): Array<Any?> {
        diskActivity()
        return result(fileSystem.size(clean(args.checkString(0))))
    }

    @Callback(direct = true, doc = """function(path:string):boolean -- Returns whether the object at the specified absolute path in the file system is a directory.""")
    @Synchronized
    fun isDirectory(context: Context, args: Arguments): Array<Any?> {
        diskActivity()
        return result(fileSystem.isDirectory(clean(args.checkString(0))))
    }

    @Callback(direct = true, doc = """function(path:string):number -- Returns the (real world) timestamp of when the object at the specified absolute path in the file system was modified.""")
    @Synchronized
    fun lastModified(context: Context, args: Arguments): Array<Any?> {
        diskActivity()
        return result(fileSystem.lastModified(clean(args.checkString(0))))
    }

    @Callback(doc = """function(path:string):table -- Returns a list of names of objects in the directory at the specified absolute path in the file system.""")
    @Synchronized
    fun list(context: Context, args: Arguments): Array<Any?>? {
        val list = fileSystem.list(clean(args.checkString(0)))
        return if (list != null) {
            diskActivity()
            arrayOf(list)
        } else null
    }

    @Callback(doc = """function(path:string):boolean -- Creates a directory at the specified absolute path in the file system. Creates parent directories, if necessary.""")
    @Synchronized
    fun makeDirectory(context: Context, args: Arguments): Array<Any?> {
        fun recurse(path: String): Boolean {
            return !fileSystem.exists(path) && (fileSystem.makeDirectory(path) ||
                (recurse(path.split("/").dropLast(1).joinToString("/")) && fileSystem.makeDirectory(path)))
        }
        val success = recurse(clean(args.checkString(0)))
        diskActivity()
        return result(success)
    }

    @Callback(doc = """function(path:string):boolean -- Removes the object at the specified absolute path in the file system.""")
    @Synchronized
    fun remove(context: Context, args: Arguments): Array<Any?> {
        fun recurse(parent: String): Boolean {
            return (!fileSystem.isDirectory(parent) ||
                fileSystem.list(parent)?.all { child -> recurse("$parent/$child") } == true) && fileSystem.delete(parent)
        }
        val success = recurse(clean(args.checkString(0)))
        diskActivity()
        return result(success)
    }

    @Callback(doc = """function(from:string, to:string):boolean -- Renames/moves an object from the first specified absolute path in the file system to the second.""")
    @Synchronized
    fun rename(context: Context, args: Arguments): Array<Any?> {
        val success = fileSystem.rename(clean(args.checkString(0)), clean(args.checkString(1)))
        diskActivity()
        return result(success)
    }

    @Callback(direct = true, doc = """function(handle:userdata) -- Closes an open file descriptor with the specified handle.""")
    @Synchronized
    fun close(context: Context, args: Arguments): Array<Any?>? {
        close(context, checkHandle(args, 0))
        return null
    }

    @Callback(direct = true, limit = 4, doc = """function(path:string[, mode:string='r']):userdata -- Opens a new file descriptor and returns its handle.""")
    @Synchronized
    fun open(context: Context, args: Arguments): Array<Any?> {
        if (owners[context.node().address()]?.size ?: 0 >= Settings.get.maxHandles) {
            throw IOException("too many open handles")
        }
        val path = args.checkString(0)
        val mode = args.optString(1, "r")
        val handle = fileSystem.open(clean(path), parseMode(mode))
        if (handle > 0) {
            owners.getOrPut(context.node().address()) { mutableSetOf() }.add(handle)
        }
        diskActivity()
        return result(HandleValue(node.address(), handle))
    }

    @Callback(direct = true, limit = 15, doc = """function(handle:userdata, count:number):string or nil -- Reads up to the specified amount of data from an open file descriptor with the specified handle. Returns nil when EOF is reached.""")
    @Synchronized
    fun read(context: Context, args: Arguments): Array<Any?> {
        context.consumeCallBudget(readCosts[speed])
        val handle = checkHandle(args, 0)
        val n = minOf(Settings.get.maxReadBuffer, maxOf(0, args.checkInteger(1)))
        checkOwner(context.node().address(), handle)

        val file = fileSystem.getHandle(handle)
            ?: throw IOException("bad file descriptor")

        // Limit size of read buffer to avoid crazy allocations.
        val buffer = ByteArray(n)
        val read = file.read(buffer)
        return if (read >= 0) {
            val bytes = if (read == buffer.size) {
                buffer
            } else {
                buffer.copyOf(read)
            }
            if (!node.tryChangeBuffer(-Settings.get.hddReadCost * bytes.size)) {
                throw IOException("not enough energy")
            }
            diskActivity()
            result(bytes)
        } else {
            result(null)
        }
    }

    @Callback(direct = true, doc = """function(handle:userdata, whence:string, offset:number):number -- Seeks in an open file descriptor with the specified handle. Returns the new pointer position.""")
    @Synchronized
    fun seek(context: Context, args: Arguments): Array<Any?> {
        context.consumeCallBudget(seekCosts[speed])
        val handle = checkHandle(args, 0)
        val whence = args.checkString(1)
        val offset = args.checkInteger(2)
        checkOwner(context.node().address(), handle)

        val file = fileSystem.getHandle(handle)
            ?: throw IOException("bad file descriptor")

        when (whence) {
            "cur" -> file.seek(file.position() + offset)
            "set" -> file.seek(offset.toLong())
            "end" -> file.seek(file.length() + offset)
            else -> throw IllegalArgumentException("invalid mode")
        }
        return result(file.position())
    }

    @Callback(direct = true, doc = """function(handle:userdata, value:string):boolean -- Writes the specified data to an open file descriptor with the specified handle.""")
    @Synchronized
    fun write(context: Context, args: Arguments): Array<Any?> {
        context.consumeCallBudget(writeCosts[speed])
        val handle = checkHandle(args, 0)
        val value = args.checkByteArray(1)
        if (!node.tryChangeBuffer(-Settings.get.hddWriteCost * value.size)) {
            throw IOException("not enough energy")
        }
        checkOwner(context.node().address(), handle)

        val file = fileSystem.getHandle(handle)
            ?: throw IOException("bad file descriptor")

        file.write(value)
        diskActivity()
        return result(true)
    }

    // ----------------------------------------------------------------------- //

    fun checkHandle(args: Arguments, index: Int): Int {
        return when {
            args.isInteger(index) -> args.checkInteger(index)
            args.isTable(index) -> {
                when (val handle = args.checkTable(index)["handle"]) {
                    is Number -> handle.toInt()
                    else -> throw IOException("bad file descriptor")
                }
            }
            else -> {
                when (val handle = args.checkAny(index)) {
                    is HandleValue -> handle.handle
                    else -> throw IOException("bad file descriptor")
                }
            }
        }
    }

    fun close(context: Context, handle: Int) {
        val file = fileSystem.getHandle(handle)
            ?: throw IOException("bad file descriptor")

        val set = owners[context.node().address()]
        if (set != null && set.remove(handle)) {
            file.close()
        } else {
            throw IOException("bad file descriptor")
        }
    }

    // ----------------------------------------------------------------------- //

    @Synchronized
    override fun onMessage(message: Message) {
        super.onMessage(message)
        if (message.name() == "computer.stopped" || message.name() == "computer.started") {
            owners[message.source().address()]?.let { set ->
                set.forEach { handle ->
                    fileSystem.getHandle(handle)?.close()
                }
                set.clear()
            }
        }
    }

    @Synchronized
    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node) {
            fileSystem.close()
        } else if (owners.containsKey(node.address())) {
            owners[node.address()]?.forEach { handle ->
                fileSystem.getHandle(handle)?.close()
            }
            owners.remove(node.address())
        }
    }

    // ----------------------------------------------------------------------- //

    @Synchronized
    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)

        val ownersList = nbt.getTagList("owners", NBT.TAG_COMPOUND)
        for (i in 0 until ownersList.tagCount()) {
            val ownerNbt = ownersList.getCompoundTagAt(i)
            val address = ownerNbt.getString("address")
            if (address.isNotEmpty()) {
                owners[address] = ownerNbt.getIntArray("handles").toMutableSet()
            }
        }

        label?.load(nbt)
        fileSystem.load(nbt.getCompoundTag("fs"))
    }

    @Synchronized
    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)

        label?.save(nbt)

        if (!SaveHandler.savingForClients) {
            val ownersNbt = NBTTagList()
            for ((address, handles) in owners) {
                val ownerNbt = NBTTagCompound()
                ownerNbt.setString("address", address)
                ownerNbt.setTag("handles", NBTTagIntArray(handles.toIntArray()))
                ownersNbt.appendTag(ownerNbt)
            }
            nbt.setTag("owners", ownersNbt)

            nbt.setNewCompoundTag("fs", fileSystem::save)
        }
    }

    // ----------------------------------------------------------------------- //

    private fun clean(path: String): String {
        val result = Files.simplifyPath(path)
        if (result.startsWith("../") || result == "..") throw FileNotFoundException(path)
        return if (result == "/" || result == ".") "" else result
    }

    private fun parseMode(value: String): Mode {
        return when (value) {
            "r", "rb" -> Mode.Read
            "w", "wb" -> Mode.Write
            "a", "ab" -> Mode.Append
            else -> throw IllegalArgumentException("unsupported mode")
        }
    }

    private fun checkOwner(owner: String, handle: Int) {
        if (!owners.containsKey(owner) || !owners[owner]!!.contains(handle)) {
            throw IOException("bad file descriptor")
        }
    }

    private fun diskActivity() {
        if (sound != null && host != null) {
            ServerPacketSender.sendFileSystemActivity(node, host, sound)
        }
    }
}

class HandleValue : AbstractValue {
    var owner = ""
    var handle = 0

    constructor()

    constructor(owner: String, handle: Int) : this() {
        this.owner = owner
        this.handle = handle
    }

    override fun dispose(context: Context) {
        super.dispose(context)
        if (context.node() != null && context.node().network() != null) {
            val node = context.node().network().node(owner)
            if (node != null) {
                when (val host = node.host()) {
                    is FileSystem -> try {
                        host.close(context, handle)
                    } catch (_: Throwable) {
                        // Ignore, already closed.
                    }
                }
            }
        }
    }

    companion object {
        private const val OwnerTag = "owner"
        private const val HandleTag = "handle"
    }

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        owner = nbt.getString(OwnerTag)
        handle = nbt.getInteger(HandleTag)
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        nbt.setString(OwnerTag, owner)
        nbt.setInteger(HandleTag, handle)
    }

    override fun toString(): String = handle.toString()
}
