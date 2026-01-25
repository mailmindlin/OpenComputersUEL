package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.network.Environment
import li.cil.oc.common.tileentity.behaviors.Behavior
import li.cil.oc.common.tileentity.behaviors.BehaviorUpdate
import li.cil.oc.common.tileentity.behaviors.NbtSeriailzable
import li.cil.oc.util.*
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.ArrayDeque

interface Hub : Environment, SidedEnvironment, Tickable {
    override fun node(): Node? = null

    val hubDelegate: Delegate

    val isConnected: Boolean
        get() = hubDelegate.plugs.any { plug ->
            plug.node?.address() != null &&
            plug.node.network() != null
        }

    val relayDelay: Int get() = hubDelegate.relayDelay
    val relayAmount: Int get() = hubDelegate.relayAmount
    val maxQueueSize: Int get() = hubDelegate.maxQueueSize
    val packetsPerCycleAvg: Int get() = hubDelegate.packetsPerCycleAvg()
    val queueSize: Int get() = hubDelegate.queue.size

    class Delegate(val tile: Hub): Behavior, NbtSeriailzable, BehaviorUpdate {
        val queue: ArrayDeque<Pair<EnumFacing?, Packet>> = ArrayDeque()
        var maxQueueSize = tile.queueBaseSize
        var relayDelay = tile.relayBaseDelay
        var relayAmount = tile.relayBaseAmount

        var relayCooldown = -1

        // 20 cycles
        val packetsPerCycleAvg = MovingAverage(20)

        internal val plugs: SidedArray<Plug> = SidedArray { side -> createPlug(side) }

        private fun createPlug(side: EnumFacing) = Plug(tile, side)

        override fun update() {
            if (relayCooldown > 0) {
                relayCooldown -= 1
            } else {
                relayCooldown = -1
                if (queue.isNotEmpty()) {
                    synchronized(queue) {
                        val packetsToRelay = minOf(queue.size, relayAmount)
                        packetsPerCycleAvg.add(packetsToRelay)
                        for (i in 0 until packetsToRelay) {
                            val (sourceSide, packet) = queue.poll()
                            tile.relayPacket(sourceSide, packet)
                        }
                        if (queue.isNotEmpty()) {
                            relayCooldown = relayDelay - 1
                        }
                    }
                } else if (tile.world != null && tile.world!!.totalWorldTime % relayDelay == 0L) {
                    packetsPerCycleAvg.add(0)
                }
            }
        }

        override fun readFromNBTForServer(nbt: NBTTagCompound) {
            super.readFromNBTForServer(nbt)
            val plugsList = nbt.getTagList(PlugsTag, NBT.TAG_COMPOUND)
            for (index in 0 until plugsList.tagCount()) {
                if (index < plugs.size) {
                    plugs[index].node?.load(plugsList.getCompoundTagAt(index))
                }
            }
            val queueList = nbt.getTagList(QueueTag, NBT.TAG_COMPOUND)
            for (i in 0 until queueList.tagCount()) {
                val tag = queueList.getCompoundTagAt(i)
                val side = tag.getDirection(SideTag)
                val packet = ApiNetwork.newPacket(tag)
                queue.add(Pair(side, packet))
            }
            if (nbt.hasKey(RelayCooldownTag)) {
                relayCooldown = nbt.getInteger(RelayCooldownTag)
            }
        }

        override fun writeToNBTForServer(nbt: NBTTagCompound) {
            synchronized(queue) {
                super.writeToNBTForServer(nbt)
                // Side check for Waila (and other mods that may call this client side).
                if (tile.isServer) {
                    nbt.setNewTagList(PlugsTag, plugs.map { plug ->
                        val plugNbt = NBTTagCompound()
                        plug.node?.save(plugNbt)
                        plugNbt
                    })
                    nbt.setNewTagList(QueueTag, queue.map { (sourceSide, packet) ->
                        val tag = NBTTagCompound()
                        tag.setDirection(SideTag, sourceSide)
                        packet.save(tag)
                        tag
                    })
                    if (relayCooldown > 0) {
                        nbt.setInteger(RelayCooldownTag, relayCooldown)
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    val queueBaseSize: Int get() = Settings.get.switchDefaultMaxQueueSize
    val queueSizePerUpgrade: Int get() = Settings.get.switchQueueSizeUpgrade
    val relayBaseDelay: Int get() = Settings.get.switchDefaultRelayDelay
    val relayDelayPerUpgrade: Int get() = Settings.get.switchRelayDelayUpgrade.toInt()
    val relayBaseAmount: Int get() = Settings.get.switchDefaultRelayAmount
    val relayAmountPerUpgrade: Int get() = Settings.get.switchRelayAmountUpgrade

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = side != null

    override fun sidedNode(side: EnumFacing?): Node? = if (side != null) hubDelegate.plugs[side.ordinal].node else null

    // ----------------------------------------------------------------------- //

    fun tryEnqueuePacket(sourceSide: EnumFacing?, packet: Packet): Boolean {
        val delegate = hubDelegate
        synchronized(delegate.queue) {
            if (packet.ttl() > 0 && delegate.queue.size < delegate.maxQueueSize) {
                delegate.queue.add(Pair(sourceSide, packet.hop()))
                if (delegate.relayCooldown < 0) {
                    delegate.relayCooldown = delegate.relayDelay - 1
                }
                return true
            }
            return false
        }
    }

    fun relayPacket(sourceSide: EnumFacing?, packet: Packet) {
        for (side in EnumFacing.values()) {
            if (sourceSide == null || sourceSide != side) {
                val node = sidedNode(side)
                if (node != null) {
                    node.sendToReachable("network.message", packet)
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val PlugsTag = Settings.namespace + "plugs"
        private val QueueTag = Settings.namespace + "queue"
        private val SideTag = "side"
        private val RelayCooldownTag = Settings.namespace + "relayCooldown"
    }

    // ----------------------------------------------------------------------- //

    open class Plug(val hub: Hub, val side: EnumFacing) : Environment {
        val node: Node? = hub.createNode(this)

        override fun node(): Node? = node

        override fun onMessage(message: Message) {
            if (isPrimary) {
                hub.onPlugMessage(this, message)
            }
        }

        override fun onConnect(node: Node) = hub.onPlugConnect(this, node)

        override fun onDisconnect(node: Node) = hub.onPlugDisconnect(this, node)

        val isPrimary: Boolean
            get() {
                val plugs = hub.hubDelegate.plugs
                val index = plugs.indexOfFirst { it.node?.network() == node?.network() }
                return index >= 0 && plugs[index] == this
            }

        val plugsInOtherNetworks: List<Plug>
            get() = hub.hubDelegate.plugs.filter { it.node?.network() != node?.network() }
    }

    fun onPlugConnect(plug: Plug, node: Node) {}

    fun onPlugDisconnect(plug: Plug, node: Node) {}

    fun onPlugMessage(plug: Plug, message: Message) {
        if (message.name() == "network.message" && hubDelegate.plugs.none { it.node == message.source() }) {
            val data = message.data()
            if (data.isNotEmpty() && data[0] is Packet) {
                tryEnqueuePacket(plug.side, data[0] as Packet)
            }
        }
    }

    fun createNode(plug: Plug): Node = ApiNetwork.newNode(plug, Visibility.Network).create()
}
