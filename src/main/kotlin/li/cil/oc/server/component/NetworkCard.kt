package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.component.RackBusConnectable
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Rack
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.*
import li.cil.oc.common.Tier
import li.cil.oc.server.component.traits.WakeMessageAware
import li.cil.oc.server.component.traits.WakeMessageHelper
import li.cil.oc.util.Result
import li.cil.oc.util.result
import net.minecraft.nbt.NBTTagCompound
import li.cil.oc.server.PacketSender as ServerPacketSender

open class NetworkCard(val host: EnvironmentHost) : ManagedEnvironmentKt(), RackBusConnectable, DeviceInfo, WakeMessageAware {
    protected val visibility: Visibility = when (host) {
        is Rack -> Visibility.Neighbors
        else -> Visibility.Network
    }

    override val node: Component? = nodeFactory(visibility)
        .withComponent("modem", Visibility.Neighbors)
        .create()

    override val wakeMessageHelper = WakeMessageHelper()

    protected val openPorts = mutableSetOf<Int>()

    // wired network card is the 1st in the max ports list (before both wireless cards)
    protected open val maxOpenPorts: Int get() = Settings.get.maxOpenPorts[Tier.One]

    // ----------------------------------------------------------------------- //

    private val deviceInfo_ by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Network,
            DeviceAttribute.Description to "Ethernet controller",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "42i520 (MPN-01)",
            DeviceAttribute.Version to "1.0",
            DeviceAttribute.Capacity to Settings.get.maxNetworkPacketSize.toString(),
            DeviceAttribute.Size to maxOpenPorts.toString(),
            DeviceAttribute.Width to Settings.get.maxNetworkPacketParts.toString()
        )
    }

    override fun getDeviceInfo() = deviceInfo_

    // ----------------------------------------------------------------------- //

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(port:number):boolean -- Opens the specified port. Returns true if the port was opened.""")
    fun open(context: Context, args: Arguments): Result {
        val port = checkPort(args.checkInteger(0))
        return if (openPorts.contains(port)) {
            result(false)
        } else if (openPorts.size >= maxOpenPorts) {
            throw java.io.IOException("too many open ports")
        } else {
            result(openPorts.add(port))
        }
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function([port:number]):boolean -- Closes the specified port (default: all ports). Returns true if ports were closed.""")
    fun close(context: Context, args: Arguments): Result {
        return if (args.count() == 0) {
            val closed = openPorts.isNotEmpty()
            openPorts.clear()
            result(closed)
        } else {
            val port = checkPort(args.checkInteger(0))
            result(openPorts.remove(port))
        }
    }

    @Suppress("unused", "unused_parameter")
    @Callback(direct = true, doc = """function(port:number):boolean -- Whether the specified port is open.""")
    fun isOpen(context: Context, args: Arguments): Result {
        val port = checkPort(args.checkInteger(0))
        return result(openPorts.contains(port))
    }

    @Suppress("unused")
    @Callback(direct = true, doc = """function():boolean -- Whether this card has wireless networking capability.""")
    open fun isWireless(context: Context, args: Arguments): Result = result(false)

    @Suppress("unused")
    @Callback(direct = true, doc = """function():boolean -- Whether this card has wired networking capability.""")
    open fun isWired(context: Context, args: Arguments): Result = result(true)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(address:string, port:number, data...) -- Sends the specified data to the specified target.""")
    fun send(context: Context, args: Arguments): Result {
        val address = args.checkString(0)
        val port = checkPort(args.checkInteger(1))
        val packet = li.cil.oc.api.Network.newPacket(node!!.address(), address, port, args.drop(2))!!
        doSend(packet)
        networkActivity()
        return result(true)
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(port:number, data...) -- Broadcasts the specified data on the specified port.""")
    fun broadcast(context: Context, args: Arguments): Result {
        val port = checkPort(args.checkInteger(0))
        val packet = li.cil.oc.api.Network.newPacket(node!!.address(), null, port, args.drop(1))!!
        doBroadcast(packet)
        networkActivity()
        return result(true)
    }

    protected open fun doSend(packet: Packet) {
        when (visibility) {
            Visibility.Neighbors -> node!!.sendToNeighbors("network.message", packet)
            Visibility.Network -> node!!.sendToReachable("network.message", packet)
            else -> {} // Ignore.
        }
    }

    protected open fun doBroadcast(packet: Packet) {
        when (visibility) {
            Visibility.Neighbors -> node!!.sendToNeighbors("network.message", packet)
            Visibility.Network -> node!!.sendToReachable("network.message", packet)
            else -> {} // Ignore.
        }
    }

    // ----------------------------------------------------------------------- //

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node) {
            openPorts.clear()
        }
    }

    override fun onMessage(message: Message) {
        super.onMessage(message)
        if ((message.name() == "computer.stopped" || message.name() == "computer.started") && node!!.isNeighborOf(message.source())) {
            openPorts.clear()
        }
        if (message.name() == "network.message") {
            val data = message.data()
            if (data.isNotEmpty() && data[0] is Packet) {
                receivePacket(data[0] as Packet)
            }
        }
    }

    override fun isPacketAccepted(packet: Packet, distance: Double): Boolean {
        if (super.isPacketAccepted(packet, distance)) {
            if (openPorts.contains(packet.port())) {
                networkActivity()
                return true
            }
        }
        return false
    }

    override fun receivePacket(packet: Packet) {
        receivePacket(packet, 0.0, host)
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private const val OpenPortsTag = "openPorts"
    }

    override fun load(nbt: NBTTagCompound) {
        super.load(nbt)
        assert(openPorts.isEmpty())
        openPorts.addAll(nbt.getIntArray(OpenPortsTag).toList())
        loadWakeMessage(nbt)
    }

    override fun save(nbt: NBTTagCompound) {
        super.save(nbt)
        nbt.setIntArray(OpenPortsTag, openPorts.toIntArray())
        saveWakeMessage(nbt)
    }

    // ----------------------------------------------------------------------- //

    protected fun checkPort(port: Int): Int {
        if (port < 1 || port > 0xFFFF) {
            throw IllegalArgumentException("invalid port number")
        }
        return port
    }

    private fun networkActivity() {
        if (host is EnvironmentHost) {
            ServerPacketSender.sendNetworkActivity(node!!, host)
        }
    }
}
