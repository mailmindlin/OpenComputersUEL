package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.ExtendedNBT.extendedNBT
import li.cil.oc.util.MovingAverage
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.ArrayDeque

abstract class Hub : Environment, SidedEnvironment, Tickable {
    override fun node(): Node? = null

    override val isConnected: Boolean
        get() = plugs.any { plug ->
            plug.node?.address() != null &&
            plug.node.network() != null
        }

    protected val plugs: Array<Plug> = EnumFacing.values().map { side -> createPlug(side) }.toTypedArray()

    val queue: ArrayDeque<Pair<EnumFacing?, Packet>> = ArrayDeque()

    var maxQueueSize = queueBaseSize

    var relayDelay = relayBaseDelay

    var relayAmount = relayBaseAmount

    var relayCooldown = -1

    // 20 cycles
    val packetsPerCycleAvg = MovingAverage(20)

    // ----------------------------------------------------------------------- //

    protected open val queueBaseSize: Int get() = Settings.get.switchDefaultMaxQueueSize

    protected open val queueSizePerUpgrade: Int get() = Settings.get.switchQueueSizeUpgrade

    protected open val relayBaseDelay: Int get() = Settings.get.switchDefaultRelayDelay

    protected open val relayDelayPerUpgrade: Int get() = Settings.get.switchRelayDelayUpgrade

    protected open val relayBaseAmount: Int get() = Settings.get.switchDefaultRelayAmount

    protected open val relayAmountPerUpgrade: Int get() = Settings.get.switchRelayAmountUpgrade

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = side != null

    override fun sidedNode(side: EnumFacing): Node? = if (side != null) plugs[side.ordinal].node else null

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        super.updateEntity()
        if (relayCooldown > 0) {
            relayCooldown -= 1
        } else {
            relayCooldown = -1
            if (queue.isNotEmpty()) {
                synchronized(queue) {
                    val packetsToRelay = minOf(queue.size, relayAmount)
                    packetsPerCycleAvg.add(packetsToRelay.toDouble())
                    for (i in 0 until packetsToRelay) {
                        val (sourceSide, packet) = queue.poll()
                        relayPacket(sourceSide, packet)
                    }
                    if (queue.isNotEmpty()) {
                        relayCooldown = relayDelay - 1
                    }
                }
            } else if (getWorld().totalWorldTime % relayDelay == 0L) {
                packetsPerCycleAvg.add(0.0)
            }
        }
    }

    fun tryEnqueuePacket(sourceSide: EnumFacing?, packet: Packet): Boolean {
        synchronized(queue) {
            if (packet.ttl() > 0 && queue.size < maxQueueSize) {
                queue.add(Pair(sourceSide, packet.hop()))
                if (relayCooldown < 0) {
                    relayCooldown = relayDelay - 1
                }
                return true
            }
            return false
        }
    }

    protected open fun relayPacket(sourceSide: EnumFacing?, packet: Packet) {
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
            val side = tag.extendedNBT().getDirection(SideTag)
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
            if (isServer) {
                nbt.extendedNBT().setNewTagList(PlugsTag, plugs.map { plug ->
                    val plugNbt = NBTTagCompound()
                    plug.node?.save(plugNbt)
                    plugNbt
                })
                nbt.extendedNBT().setNewTagList(QueueTag, queue.map { (sourceSide, packet) ->
                    val tag = NBTTagCompound()
                    tag.extendedNBT().setDirection(SideTag, sourceSide)
                    packet.save(tag)
                    tag
                })
                if (relayCooldown > 0) {
                    nbt.setInteger(RelayCooldownTag, relayCooldown)
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    protected open fun createPlug(side: EnumFacing): Plug = Plug(side)

    open inner class Plug(val side: EnumFacing) : api.network.Environment {
        val node: Node? = createNode(this)

        override fun node(): Node? = node

        override fun onMessage(message: Message) {
            if (isPrimary) {
                onPlugMessage(this, message)
            }
        }

        override fun onConnect(node: Node) = onPlugConnect(this, node)

        override fun onDisconnect(node: Node) = onPlugDisconnect(this, node)

        val isPrimary: Boolean
            get() {
                val index = plugs.indexOfFirst { it.node?.network() == node?.network() }
                return index >= 0 && plugs[index] == this
            }

        val plugsInOtherNetworks: List<Plug>
            get() = plugs.filter { it.node?.network() != node?.network() }
    }

    protected open fun onPlugConnect(plug: Plug, node: Node) {}

    protected open fun onPlugDisconnect(plug: Plug, node: Node) {}

    protected open fun onPlugMessage(plug: Plug, message: Message) {
        if (message.name() == "network.message" && plugs.none { it.node == message.source() }) {
            val data = message.data()
            if (data.isNotEmpty() && data[0] is Packet) {
                tryEnqueuePacket(plug.side, data[0] as Packet)
            }
        }
    }

    protected open fun createNode(plug: Plug): Node? = ApiNetwork.newNode(plug, Visibility.Network).create()
}
