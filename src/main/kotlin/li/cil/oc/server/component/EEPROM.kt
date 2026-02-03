package li.cil.oc.server.component

import com.google.common.hash.Hashing
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.Result
import li.cil.oc.util.result
import net.minecraft.nbt.NBTTagCompound

class EEPROM : ManagedEnvironmentKt(), DeviceInfo {
    override val node = nodeFactory(Visibility.Neighbors)
        .withComponent("eeprom", Visibility.Neighbors)
        .withConnector()
        .create()

    private var codeData = ByteArray(0)
    private var volatileData = ByteArray(0)
    var readonly = false
    var label = "EEPROM"

    private val checksum: String
        get() = Hashing.crc32().hashBytes(codeData).toString()

    // ----------------------------------------------------------------------- //

    override fun getDeviceInfo() = Companion.deviceInfo

    // ----------------------------------------------------------------------- //

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function():string -- Get the currently stored byte array.""")
    fun get(context: Context, args: Arguments): Result = result(codeData)

    @Callback(doc = """function(data:string) -- Overwrite the currently stored byte array.""")
    fun set(context: Context, args: Arguments): Result? {
        if (readonly) {
            return result(Unit, "storage is readonly")
        }
        if (!node!!.tryChangeBuffer(-Settings.get.eepromWriteCost)) {
            return result(Unit, "not enough energy")
        }
        val newData = args.optByteArray(0, ByteArray(0))
        if (newData.size > Settings.get.eepromSize) {
            throw IllegalArgumentException("not enough space")
        }
        codeData = newData
        context.pause(2.0) // deliberately slow to discourage use as normal storage medium
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function():string -- Get the label of the EEPROM.""")
    fun getLabel(context: Context, args: Arguments): Result = result(label)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(data:string):string -- Set the label of the EEPROM.""")
    fun setLabel(context: Context, args: Arguments): Result {
        if (readonly) {
            return result(Unit, "storage is readonly")
        }
        label = args.optString(0, "EEPROM").trim().take(24)
        if (label.isEmpty()) {
            label = "EEPROM"
        }
        return result(label)
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function():number -- Get the storage capacity of this EEPROM.""")
    fun getSize(context: Context, args: Arguments): Result = result(Settings.get.eepromSize)

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function():string -- Get the checksum of the data on this EEPROM.""")
    fun getChecksum(context: Context, args: Arguments): Result = result(checksum)

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function(checksum:string):boolean -- Make this EEPROM readonly if it isn't already. This process cannot be reversed!""")
    fun makeReadonly(context: Context, args: Arguments): Result {
        if (args.checkString(0) != checksum)
            return result(Unit, "incorrect checksum")
        readonly = true
        return result(true)
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function():number -- Get the storage capacity of this EEPROM.""")
    fun getDataSize(context: Context, args: Arguments): Result = result(Settings.get.eepromDataSize)

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function():string -- Get the currently stored byte array.""")
    fun getData(context: Context, args: Arguments): Result = result(volatileData)

    @Suppress("unused")
    @Callback(doc = """function(data:string) -- Overwrite the currently stored byte array.""")
    fun setData(context: Context, args: Arguments): Result? {
        if (!node!!.tryChangeBuffer(-Settings.get.eepromWriteCost))
            return result(Unit, "not enough energy")
        val newData = args.optByteArray(0, ByteArray(0))
        if (newData.size > Settings.get.eepromDataSize)
            throw IllegalArgumentException("not enough space")
        volatileData = newData
        context.pause(1.0) // deliberately slow to discourage use as normal storage medium
        return null
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private const val EEPROMTag = Settings.namespace + "eeprom"
        private const val LabelTag = Settings.namespace + "label"
        private const val ReadonlyTag = Settings.namespace + "readonly"
        private const val UserdataTag = Settings.namespace + "userdata"

        private val deviceInfo = mapOf(
            DeviceAttribute.Class to DeviceClass.Memory,
            DeviceAttribute.Description to "EEPROM",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "FlashStick2k",
            DeviceAttribute.Capacity to Settings.get.eepromSize.toString(),
            DeviceAttribute.Size to Settings.get.eepromSize.toString()
        )
    }

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        codeData = nbt.getByteArray(EEPROMTag)
        if (nbt.hasKey(LabelTag)) {
            label = nbt.getString(LabelTag)
        }
        readonly = nbt.getBoolean(ReadonlyTag)
        volatileData = nbt.getByteArray(UserdataTag)
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        nbt.setByteArray(EEPROMTag, codeData)
        nbt.setString(LabelTag, label)
        nbt.setBoolean(ReadonlyTag, readonly)
        nbt.setByteArray(UserdataTag, volatileData)
    }
}
