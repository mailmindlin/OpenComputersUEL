package li.cil.oc.server.component

import codechicken.lib.vec.Vector3
import codechicken.wirelessredstone.api.WirelessReceivingDevice
import codechicken.wirelessredstone.api.WirelessTransmittingDevice
import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Node
import li.cil.oc.common.EventHandler
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.WirelessRedstone
import net.minecraft.entity.EntityLivingBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.common.Optional

@Optional.InterfaceList(
    Optional.Interface(iface = "codechicken.wirelessredstone.api.WirelessReceivingDevice", modid = Mods.IDs.WirelessRedstoneCBE),
    Optional.Interface(iface = "codechicken.wirelessredstone.api.WirelessTransmittingDevice", modid = Mods.IDs.WirelessRedstoneCBE)
)
abstract class RedstoneWireless : RedstoneSignaller(), DeviceInfo, WirelessReceivingDevice, WirelessTransmittingDevice {
    abstract val redstone: EnvironmentHost

    var wirelessFrequency = 0

    var wirelessInput = false

    var wirelessOutput = false

    // ----------------------------------------------------------------------- //

    private val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Communication,
            DeviceAttribute.Description to "Wireless redstone controller",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Rw400-M",
            DeviceAttribute.Capacity to "1",
            DeviceAttribute.Width to "1"
        )
    }

    override fun getDeviceInfo(): MutableMap<String, String> = deviceInfo.toMutableMap()

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function():number -- Get the wireless redstone input.""")
    fun getWirelessInput(context: Context, args: Arguments): Array<Any?> {
        wirelessInput = WirelessRedstone.getInput(this)
        return result(wirelessInput)
    }

    @Callback(direct = true, doc = """function():boolean -- Get the wireless redstone output.""")
    fun getWirelessOutput(context: Context, args: Arguments): Array<Any?> = result(wirelessOutput)

    @Callback(doc = """function(value:boolean):boolean -- Set the wireless redstone output.""")
    fun setWirelessOutput(context: Context, args: Arguments): Array<Any?> {
        val oldValue = wirelessOutput
        val newValue = args.checkBoolean(0)

        if (oldValue != newValue) {
            wirelessOutput = newValue

            WirelessRedstone.updateOutput(this)

            if (Settings.get.redstoneDelay > 0) {
                context.pause(Settings.get.redstoneDelay)
            }
        }

        return result(oldValue)
    }

    @Callback(direct = true, doc = """function():number -- Get the currently set wireless redstone frequency.""")
    fun getWirelessFrequency(context: Context, args: Arguments): Array<Any?> = result(wirelessFrequency)

    @Callback(doc = """function(frequency:number):number -- Set the wireless redstone frequency to use.""")
    fun setWirelessFrequency(context: Context, args: Arguments): Array<Any?> {
        val oldValue = wirelessFrequency
        val newValue = args.checkInteger(0)

        if (oldValue != newValue) {
            WirelessRedstone.removeReceiver(this)
            WirelessRedstone.removeTransmitter(this)

            wirelessFrequency = newValue
            wirelessInput = false
            wirelessOutput = false

            WirelessRedstone.addReceiver(this)

            context.pause(0.5)
        }

        return result(oldValue)
    }

    // ----------------------------------------------------------------------- //

    @Optional.Method(modid = Mods.IDs.WirelessRedstoneCBE)
    override fun updateDevice(frequency: Int, on: Boolean) {
        if (frequency == wirelessFrequency && on != wirelessInput) {
            wirelessInput = on
            onRedstoneChanged(RedstoneChangedEventArgs(null, if (on) 0 else 1, if (on) 1 else 0))
        }
    }

    @Optional.Method(modid = Mods.IDs.WirelessRedstoneCBE)
    override fun getTransmitPos(): Vector3 = throw NotImplementedError("Requires WirelessRedstoneCBE")

    @Optional.Method(modid = Mods.IDs.WirelessRedstoneCBE)
    override fun getDimension(): Int = redstone.world().provider.dimension

    @Optional.Method(modid = Mods.IDs.WirelessRedstoneCBE)
    override fun getFreq(): Int = wirelessFrequency

    @Optional.Method(modid = Mods.IDs.WirelessRedstoneCBE)
    override fun getAttachedEntity(): EntityLivingBase? = null

    // ----------------------------------------------------------------------- //

    override fun onConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node()) {
            EventHandler.scheduleWirelessRedstone(this)
        }
    }

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node()) {
            WirelessRedstone.removeReceiver(this)
            WirelessRedstone.removeTransmitter(this)
            wirelessOutput = false
            wirelessFrequency = 0
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private const val WirelessFrequencyTag = "wirelessFrequency"
        private const val WirelessInputTag = "wirelessInput"
        private const val WirelessOutputTag = "wirelessOutput"
    }

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        wirelessFrequency = nbt.getInteger(WirelessFrequencyTag)
        wirelessInput = nbt.getBoolean(WirelessInputTag)
        wirelessOutput = nbt.getBoolean(WirelessOutputTag)
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        nbt.setInteger(WirelessFrequencyTag, wirelessFrequency)
        nbt.setBoolean(WirelessInputTag, wirelessInput)
        nbt.setBoolean(WirelessOutputTag, wirelessOutput)
    }
}
