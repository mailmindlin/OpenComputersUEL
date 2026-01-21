package li.cil.oc.server.component

import com.google.common.io.Files
import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.fs.Label
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.DimensionManager
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import li.cil.oc.server.PacketSender as ServerPacketSender

class Drive(
    val capacity: Int,
    val platterCount: Int,
    val label: Label?,
    val host: EnvironmentHost?,
    val sound: String?,
    val speed: Int,
    val isLocked: Boolean
) : ManagedEnvironmentKt(), DeviceInfoKt {
    override val node = Network.newNode(this, Visibility.Network)
        .withComponent("drive", Visibility.Neighbors)
        .withConnector()
        .create()

    private val savePath: File
        get() = File(DimensionManager.getCurrentSaveRootDirectory(), Settings.savePath + node.address() + ".bin")

    private val sectorSize = 512
    private val data = ByteArray(capacity)
    private val sectorCount = capacity / sectorSize
    private val sectorsPerPlatter = sectorCount / platterCount
    private var headPos = 0

    val readSectorCosts = doubleArrayOf(1.0 / 10, 1.0 / 20, 1.0 / 30, 1.0 / 40, 1.0 / 50, 1.0 / 60)
    val writeSectorCosts = doubleArrayOf(1.0 / 5, 1.0 / 10, 1.0 / 15, 1.0 / 20, 1.0 / 25, 1.0 / 30)
    val readByteCosts = doubleArrayOf(1.0 / 48, 1.0 / 64, 1.0 / 80, 1.0 / 96, 1.0 / 112, 1.0 / 128)
    val writeByteCosts = doubleArrayOf(1.0 / 24, 1.0 / 32, 1.0 / 40, 1.0 / 48, 1.0 / 56, 1.0 / 64)

    // ----------------------------------------------------------------------- //

    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Disk,
        DeviceAttribute.Description to "Hard disk drive",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "MPD${capacity / 1024}L$platterCount",
        DeviceAttribute.Capacity to (capacity * 1.024).toInt().toString(),
        DeviceAttribute.Size to capacity.toString(),
        DeviceAttribute.Clock to "${(2000 / readSectorCosts[speed]).toInt() / 100}/${(2000 / writeSectorCosts[speed]).toInt() / 100}/${(2000 / readByteCosts[speed]).toInt() / 100}/${(2000 / writeByteCosts[speed]).toInt() / 100}"
    )

    // ----------------------------------------------------------------------- //

    @Callback(direct = true, doc = """function():string -- Get the current label of the drive.""")
    @Synchronized
    fun getLabel(context: Context, args: Arguments): Array<Any?>? {
        return if (label != null) result(label.label) else null
    }

    @Callback(doc = """function(value:string):string -- Sets the label of the drive. Returns the new value, which may be truncated.""")
    @Synchronized
    fun setLabel(context: Context, args: Arguments): Array<Any?> {
        if (isLocked) throw Exception("drive is read only")
        if (label == null) throw Exception("drive does not support labeling")
        if (args.checkAny(0) == null) label.setLabel(null) else label.setLabel(args.checkString(0))
        return result(label.label)
    }

    @Callback(direct = true, doc = """function():number -- Returns the total capacity of the drive, in bytes.""")
    fun getCapacity(context: Context, args: Arguments): Array<Any?> = result(capacity)

    @Callback(direct = true, doc = """function():number -- Returns the size of a single sector on the drive, in bytes.""")
    fun getSectorSize(context: Context, args: Arguments): Array<Any?> = result(sectorSize)

    @Callback(direct = true, doc = """function():number -- Returns the number of platters in the drive.""")
    fun getPlatterCount(context: Context, args: Arguments): Array<Any?> = result(platterCount)

    @Callback(direct = true, doc = """function(sector:number):string -- Read the current contents of the specified sector.""")
    @Synchronized
    fun readSector(context: Context, args: Arguments): Array<Any?> {
        context.consumeCallBudget(readSectorCosts[speed])
        val sector = moveToSector(context, checkSector(args, 0))
        diskActivity()
        val sectorData = ByteArray(sectorSize)
        System.arraycopy(data, sectorOffset(sector), sectorData, 0, sectorSize)
        return result(sectorData)
    }

    @Callback(direct = true, doc = """function(sector:number, value:string) -- Write the specified contents to the specified sector.""")
    @Synchronized
    fun writeSector(context: Context, args: Arguments): Array<Any?>? {
        if (isLocked) throw Exception("drive is read only")
        context.consumeCallBudget(writeSectorCosts[speed])
        val sectorData = args.checkByteArray(1)
        val sector = moveToSector(context, checkSector(args, 0))
        diskActivity()
        System.arraycopy(sectorData, 0, data, sectorOffset(sector), minOf(sectorSize, sectorData.size))
        return null
    }

    @Callback(direct = true, doc = """function(offset:number):number -- Read a single byte at the specified offset.""")
    @Synchronized
    fun readByte(context: Context, args: Arguments): Array<Any?> {
        context.consumeCallBudget(readByteCosts[speed])
        val offset = args.checkInteger(0) - 1
        moveToSector(context, checkSector(offset))
        diskActivity()
        return result(data[offset].toInt())
    }

    @Callback(direct = true, doc = """function(offset:number, value:number) -- Write a single byte to the specified offset.""")
    @Synchronized
    fun writeByte(context: Context, args: Arguments): Array<Any?>? {
        if (isLocked) throw Exception("drive is read only")
        context.consumeCallBudget(writeByteCosts[speed])
        val offset = args.checkInteger(0) - 1
        val value = args.checkInteger(1).toByte()
        moveToSector(context, checkSector(offset))
        diskActivity()
        data[offset] = value
        return null
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private const val HeadPosTag = "headPos"
    }

    @Synchronized
    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)

        if (node.address() != null) {
            try {
                val path = savePath
                if (path.exists()) {
                    val bin = ByteArrayInputStream(Files.toByteArray(path))
                    val zin = GZIPInputStream(bin)
                    var offset = 0
                    var read: Int
                    while (offset < data.size) {
                        read = zin.read(data, offset, data.size - offset)
                        if (read < 0) break
                        offset += read
                    }
                }
            } catch (t: Throwable) {
                OpenComputers.log.warn("Failed loading drive contents for '${node.address()}'.", t)
            }
        }

        headPos = nbt.getInteger(HeadPosTag).coerceIn(0, sectorToHeadPos(sectorCount))

        label?.load(nbt)
    }

    @Synchronized
    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)

        if (node.address() != null) {
            try {
                val path = savePath
                path.parentFile.mkdirs()
                val bos = ByteArrayOutputStream()
                val zos = GZIPOutputStream(bos)
                zos.write(data)
                zos.close()
                Files.write(bos.toByteArray(), path)
            } catch (t: Throwable) {
                OpenComputers.log.warn("Failed saving drive contents for '${node.address()}'.", t)
            }
        }

        nbt.setInteger(HeadPosTag, headPos)

        label?.save(nbt)
    }

    // ----------------------------------------------------------------------- //

    private fun validateSector(sector: Int): Int {
        if (sector < 0 || sector >= sectorCount) {
            throw IllegalArgumentException("invalid offset, not in a usable sector")
        }
        return sector
    }

    private fun checkSector(offset: Int) = validateSector(offsetSector(offset))

    private fun checkSector(args: Arguments, n: Int) = validateSector(args.checkInteger(n) - 1)

    private fun moveToSector(context: Context, sector: Int): Int {
        val newHeadPos = sectorToHeadPos(sector)
        if (headPos != newHeadPos) {
            val delta = kotlin.math.abs(headPos - newHeadPos)
            if (delta > Settings.get.sectorSeekThreshold) {
                context.pause(Settings.get.sectorSeekTime)
            }
            headPos = newHeadPos
        }
        return sector
    }

    private fun sectorToHeadPos(sector: Int) = sector % sectorsPerPlatter

    private fun sectorOffset(sector: Int) = sector * sectorSize

    private fun offsetSector(offset: Int) = offset / sectorSize

    private fun diskActivity() {
        if (sound != null && host != null) {
            ServerPacketSender.sendFileSystemActivity(node, host, sound)
        }
    }
}
