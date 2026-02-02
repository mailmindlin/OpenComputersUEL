package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.Tier
import li.cil.oc.server.component.traits.WakeMessageAware
import li.cil.oc.server.component.traits.WakeMessageHelper
import li.cil.oc.server.network.QuantumNetwork
import li.cil.oc.server.network.QuantumNode
import net.minecraft.nbt.NBTTagCompound

class LinkedCard : ManagedEnvironmentKt(), QuantumNode, DeviceInfo, WakeMessageAware {
    override val node = nodeFactory(Visibility.Network)
        .withComponent("tunnel", Visibility.Neighbors)
        .withConnector()
        .create()

    override val wakeMessageHelper = WakeMessageHelper()

    override var tunnel: String = "creative"

    // ----------------------------------------------------------------------- //

    private val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Network,
        DeviceAttribute.Description to "Quantumnet controller",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "HyperLink IV: Ender Edition",
        DeviceAttribute.Capacity to Settings.get.maxNetworkPacketSize.toString(),
        DeviceAttribute.Width to Settings.get.maxNetworkPacketParts.toString()
    )

    override fun getDeviceInfo() = deviceInfo

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function(data...) -- Sends the specified data to the card this one is linked to.""")
    fun send(context: Context, args: Arguments): Array<Any?> {
        val endpoints = QuantumNetwork.getEndpoints(tunnel).filter { it != this }
        // Convert args to array to use Scala's toArray instead of the Arguments' one (which converts byte arrays to Strings).
        val argsIterable = args as Iterable<*>
        val packet = Network.newPacket(node!!.address(), null, 0, argsIterable.toList().toTypedArray())!!

        val cost = -(packet.size() / 32.0 + Settings.get.wirelessCostPerRange[Tier.Two] * Settings.get.maxWirelessRange[Tier.Two] * 5)
        return if (node.tryChangeBuffer(cost)) {
            for (endpoint in endpoints) {
                endpoint.receivePacket(packet)
            }
            result(true)
        } else {
            result(null, "not enough energy")
        }
    }

    @Callback(direct = true, doc = "function():number -- Gets the maximum packet size (config setting).")
    fun maxPacketSize(context: Context, args: Arguments): Array<Any?> {
        return result(Settings.get.maxNetworkPacketSize)
    }

    override fun receivePacket(packet: Packet) {
        receivePacket(packet, 0.0, null)
    }

    @Callback(direct = true, doc = "function():string -- Gets this link card's shared channel address")
    fun getChannel(context: Context, args: Arguments): Array<Any?> {
        return result(this.tunnel)
    }

    // ----------------------------------------------------------------------- //

    override fun onConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node) {
            QuantumNetwork.add(this)
        }
    }

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node) {
            QuantumNetwork.remove(this)
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val TunnelTag = Settings.namespace + "tunnel"
    }

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        if (nbt.hasKey(TunnelTag)) {
            tunnel = nbt.getString(TunnelTag)
        }
        loadWakeMessage(nbt)
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        nbt.setString(TunnelTag, tunnel)
        saveWakeMessage(nbt)
    }
}
