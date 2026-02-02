package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.*
import li.cil.oc.common.Tier
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.isBlockLoaded
import net.minecraft.nbt.NBTTagCompound
import java.io.IOException
import kotlin.math.sqrt

abstract class WirelessNetworkCard(host: EnvironmentHost) : NetworkCard(host), WirelessEndpoint {
    override val node = nodeFactory(Visibility.Network)
        .withComponent("modem", Visibility.Neighbors)
        .withConnector()
        .create()

    protected abstract val wirelessCostPerRange: Double

    protected abstract val maxWirelessRange: Double

    protected abstract val shouldSendWiredTraffic: Boolean

    var strength = maxWirelessRange

    private val position get() = BlockPosition(host)

    override fun x(): Int = position.x

    override fun y(): Int = position.y

    override fun z(): Int = position.z

    override fun world() = host.world

    override fun receivePacket(packet: Packet, source: WirelessEndpoint) {
        val dx = (source.x() + 0.5) - host.xPosition()
        val dy = (source.y() + 0.5) - host.yPosition()
        val dz = (source.z() + 0.5) - host.zPosition()
        val distance = sqrt(dx * dx + dy * dy + dz * dz)
        receivePacket(packet, distance, host)
    }

    // ----------------------------------------------------------------------- //

    @Callback(direct = true, doc = "function():number -- Get the signal strength (range) used when sending messages.")
    fun getStrength(context: Context, args: Arguments): Array<Any?> = result(strength)

    @Callback(doc = "function(strength:number):number -- Set the signal strength (range) used when sending messages.")
    fun setStrength(context: Context, args: Arguments): Array<Any?> {
        strength = args.checkDouble(0).coerceIn(0.0, maxWirelessRange)
        return result(strength)
    }

    override fun isWireless(context: Context, args: Arguments): Array<Any?> = result(true)

    override fun isWired(context: Context, args: Arguments): Array<Any?> = result(shouldSendWiredTraffic)

    override fun doSend(packet: Packet) {
        if (strength > 0) {
            checkPower()
            Network.sendWirelessPacket(this, strength, packet)
        }
        if (shouldSendWiredTraffic) {
            super.doSend(packet)
        }
    }

    override fun doBroadcast(packet: Packet) {
        if (strength > 0) {
            checkPower()
            Network.sendWirelessPacket(this, strength, packet)
        }
        if (shouldSendWiredTraffic) {
            super.doBroadcast(packet)
        }
    }

    private fun checkPower() {
        val cost = wirelessCostPerRange
        if (cost > 0 && !Settings.get.ignorePower) {
            if (!node!!.tryChangeBuffer(-strength * cost)) {
                throw IOException("not enough energy")
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun canUpdate(): Boolean = true

    override fun update() {
        super.update()
        if (world().totalWorldTime % 20 == 0L) {
            Network.updateWirelessNetwork(this)
        }
    }

    override fun onConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node) {
            Network.joinWirelessNetwork(this)
        }
    }

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node || !world().isBlockLoaded(position)) {
            Network.leaveWirelessNetwork(this)
        }
    }

    // ----------------------------------------------------------------------- //

    private val StrengthTag = "strength"

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        if (nbt.hasKey(StrengthTag)) {
            strength = nbt.getDouble(StrengthTag).coerceIn(0.0, maxWirelessRange)
        }
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        nbt.setDouble(StrengthTag, strength)
    }

    open class Tier1(host: EnvironmentHost) : WirelessNetworkCard(host) {
        override val wirelessCostPerRange: Double
            get() = Settings.get.wirelessCostPerRange[Tier.One]

        override val maxWirelessRange: Double
            get() = Settings.get.maxWirelessRange[Tier.One]

        // wired network card is before wireless cards in max port list
        override val maxOpenPorts: Int
            get() = Settings.get.maxOpenPorts[Tier.One + 1]

        override val shouldSendWiredTraffic: Boolean = false

        // ----------------------------------------------------------------------- //

        protected open val deviceInfo_ by lazy {
            mapOf(
                DeviceAttribute.Class to DeviceClass.Network,
                DeviceAttribute.Description to "Wireless ethernet controller",
                DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
                DeviceAttribute.Product to "39i110 (LPPW-01)",
                DeviceAttribute.Version to "1.0",
                DeviceAttribute.Capacity to Settings.get.maxNetworkPacketSize.toString(),
                DeviceAttribute.Size to maxOpenPorts.toString(),
                DeviceAttribute.Width to maxWirelessRange.toString()
            )
        }
        override fun getDeviceInfo() = deviceInfo_

        override fun isPacketAccepted(packet: Packet, distance: Double): Boolean {
            return if (distance <= maxWirelessRange && (distance > 0 || shouldSendWiredTraffic)) {
                super.isPacketAccepted(packet, distance)
            } else {
                false
            }
        }
    }

    class Tier2(host: EnvironmentHost) : Tier1(host) {
        override val wirelessCostPerRange: Double
            get() = Settings.get.wirelessCostPerRange[Tier.Two]

        override val maxWirelessRange: Double
            get() = Settings.get.maxWirelessRange[Tier.Two]

        // wired network card is before wireless cards in max port list
        override val maxOpenPorts: Int
            get() = Settings.get.maxOpenPorts[Tier.Two + 1]

        override val shouldSendWiredTraffic: Boolean = true

        // ----------------------------------------------------------------------- //

        override val deviceInfo_ = mapOf(
            DeviceAttribute.Class to DeviceClass.Network,
            DeviceAttribute.Description to "Wireless ethernet controller",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "62i230 (MPW-01)",
            DeviceAttribute.Version to "2.0",
            DeviceAttribute.Capacity to Settings.get.maxNetworkPacketSize.toString(),
            DeviceAttribute.Size to maxOpenPorts.toString(),
            DeviceAttribute.Width to maxWirelessRange.toString()
        )
    }
}
