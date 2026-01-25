package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.component.RackMountable
import li.cil.oc.api.internal.Rack as InternalRack
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.util.StateAware
import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity.traits.*
import li.cil.oc.common.tileentity.traits.BundledRedstoneAware
import li.cil.oc.common.tileentity.traits.Hub
import li.cil.oc.common.tileentity.traits.Rotatable
import li.cil.oc.common.tileentity.traits.ComponentInventory as TraitComponentInventory
import li.cil.oc.common.tileentity.traits.power.IndustrialCraft2Experimental
import li.cil.oc.integration.opencomputers.DriverRedstoneCard
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.util.setNewCompoundTag
import li.cil.oc.util.setNewTagList
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagIntArray
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.EnumSet
import li.cil.oc.common.tileentity.traits.PowerAcceptor as TraitPowerAcceptor
import li.cil.oc.common.tileentity.traits.Hub as TraitHub
import li.cil.oc.common.tileentity.traits.PowerBalancer as TraitPowerBalancer
import li.cil.oc.common.tileentity.traits.Rotatable as TraitRotatable
import li.cil.oc.common.tileentity.traits.BundledRedstoneAware as TraitBundledRedstoneAware
import li.cil.oc.common.tileentity.traits.StateAware as TraitStateAware

class Rack : TileEntityBase(), TraitPowerAcceptor, TraitHub, TraitPowerBalancer, TraitComponentInventory, TraitRotatable, TraitBundledRedstoneAware, Analyzable, InternalRack, TraitStateAware {
    override val rotatableDelegate: Rotatable.RotatableDelegate = register(Rotatable::RotatableDelegate)
    override val redstoneDelegate: BundledRedstoneAware.Delegate = register(BundledRedstoneAware::Delegate)
    override val ic2Delegate: IndustrialCraft2Experimental.Delegate = register(IndustrialCraft2Experimental::Delegate)
    @JvmField
    var isRelayEnabled = false

    @JvmField
    val lastData = arrayOfNulls<NBTTagCompound>(sizeInventory)

    @JvmField
    val hasChanged: Array<Boolean> = Array(sizeInventory) { true }

    // Map node connections for each installed mountable. Each mountable may
    // have up to four outgoing connections, with the first one always being
    // the "primary" connection, i.e. being a direct connection allowing
    // component access (i.e. actually connecting to that side of the rack).
    // The other nodes are "secondary" connections and merely transfer network
    // messages.
    // mountable -> connectable -> side
    @JvmField
    val nodeMapping: Array<Array<EnumFacing?>> = Array(sizeInventory) { arrayOfNulls<EnumFacing>(4) }

    @JvmField
    val snifferNodes: Array<Array<Node>> = Array(sizeInventory) {
        Array(3) { ApiNetwork.newNode(this, Visibility.Neighbors).create() }
    }

    fun connect(slot: Int, connectableIndex: Int, side: EnumFacing?) {
        val newSide = when {
            side != null && side != EnumFacing.SOUTH -> side
            else -> null
        }

        val oldSide = nodeMapping[slot][connectableIndex + 1]
        if (oldSide == newSide) return

        // Cut connection / remove sniffer node.
        val mountable = getMountable(slot)
        if (mountable != null && oldSide != null) {
            if (connectableIndex == -1) {
                val node = mountable.node()
                val plug = sidedNode(toGlobal(oldSide))
                if (node != null && plug != null) {
                    node.disconnect(plug)
                }
            } else if (connectableIndex >= 0) {
                snifferNodes[slot][connectableIndex].remove()
            }
        }

        nodeMapping[slot][connectableIndex + 1] = newSide

        // Establish connection / add sniffer node.
        if (mountable != null && newSide != null) {
            if (connectableIndex == -1) {
                val node = mountable.node()
                val plug = sidedNode(toGlobal(newSide))
                if (node != null && plug != null) {
                    node.connect(plug)
                }
            } else if (connectableIndex >= 0 && connectableIndex < mountable.connectableCount) {
                val connectable = mountable.getConnectableAt(connectableIndex)
                if (connectable != null && connectable.node() != null) {
                    if (connectable.node().network() == null) {
                        ApiNetwork.joinNewNetwork(connectable.node())
                    }
                    connectable.node().connect(snifferNodes[slot][connectableIndex])
                }
            }
        }
    }

    private fun reconnect(plugSide: EnumFacing) {
        for (slot in 0 until sizeInventory) {
            val mapping = nodeMapping[slot]
            mapping[0]?.let { side ->
                if (toGlobal(side) == plugSide) {
                    val mountable = getMountable(slot)
                    val busNode = sidedNode(plugSide)
                    if (busNode != null && mountable != null && mountable.node() != null && busNode != mountable.node()) {
                        ApiNetwork.joinNewNetwork(mountable.node())
                        busNode.connect(mountable.node())
                    }
                }
            }
            for (connectableIndex in 0 until 3) {
                mapping[connectableIndex + 1]?.let { side ->
                    if (toGlobal(side) == plugSide) {
                        val mountable = getMountable(slot)
                        if (mountable != null && connectableIndex < mountable.connectableCount) {
                            val connectable = mountable.getConnectableAt(connectableIndex)
                            if (connectable != null && connectable.node() != null) {
                                if (connectable.node().network() == null) {
                                    ApiNetwork.joinNewNetwork(connectable.node())
                                }
                                connectable.node().connect(snifferNodes[slot][connectableIndex])
                            }
                        }
                    }
                }
            }
        }
    }

    protected fun sendPacketToMountables(sourceSide: EnumFacing?, packet: Packet) {
        // When a message arrives on a bus, also send it to all secondary nodes
        // connected to it. Only deliver it to that very node, if it's not the
        // sender, to avoid loops.
        for (slot in 0 until sizeInventory) {
            val mapping = nodeMapping[slot]
            for (connectableIndex in 0 until 3) {
                mapping[connectableIndex + 1]?.let { side ->
                    if (sourceSide != null && toGlobal(side) == sourceSide) {
                        val mountable = getMountable(slot)
                        if (mountable != null && connectableIndex < mountable.connectableCount) {
                            val connectable = mountable.getConnectableAt(connectableIndex)
                            connectable?.receivePacket(packet)
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //
    // Hub

    override fun tryEnqueuePacket(sourceSide: EnumFacing?, packet: Packet): Boolean {
        sendPacketToMountables(sourceSide, packet)
        return if (isRelayEnabled) super.tryEnqueuePacket(sourceSide, packet) else true
    }

    override fun relayPacket(sourceSide: EnumFacing?, packet: Packet) {
        if (isRelayEnabled) super.relayPacket(sourceSide, packet)
    }

    override fun onPlugConnect(plug: Hub.Plug, node: Node) {
        super.onPlugConnect(plug, node)
        connectComponents()
        reconnect(plug.side)
    }

    override fun createNode(plug: Hub.Plug): Node = ApiNetwork.newNode(plug, Visibility.Network)
        .withConnector(Settings.get.bufferDistributor)
        .create()

    // ----------------------------------------------------------------------- //
    // Environment

    override fun dispose() {
        super.dispose()
        disconnectComponents()
    }

    override fun onMessage(message: Message) {
        super.onMessage(message)
        if (message.name() == "network.message") {
            val data = message.data()
            if (data.isNotEmpty() && data[0] is Packet) {
                relayIfMessageFromConnectable(message, data[0] as Packet)
            }
        }
    }

    private fun relayIfMessageFromConnectable(message: Message, packet: Packet) {
        for (slot in 0 until sizeInventory) {
            val mountable = getMountable(slot)
            if (mountable != null) {
                val mapping = nodeMapping[slot]
                for (connectableIndex in 0 until 3) {
                    mapping[connectableIndex + 1]?.let { side ->
                        if (connectableIndex < mountable.connectableCount) {
                            val connectable = mountable.getConnectableAt(connectableIndex)
                            if (connectable != null && connectable.node() == message.source()) {
                                sidedNode(toGlobal(side))?.sendToReachable("network.message", packet)
                                relayToConnectablesOnSide(message, packet, side)
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    private fun relayToConnectablesOnSide(message: Message, packet: Packet, sourceSide: EnumFacing) {
        for (slot in 0 until sizeInventory) {
            val mountable = getMountable(slot)
            if (mountable != null) {
                val mapping = nodeMapping[slot]
                for (connectableIndex in 0 until 3) {
                    mapping[connectableIndex + 1]?.let { side ->
                        if (side == sourceSide && connectableIndex < mountable.connectableCount) {
                            val connectable = mountable.getConnectableAt(connectableIndex)
                            if (connectable != null && connectable.node() != message.source()) {
                                snifferNodes[slot][connectableIndex].sendToNeighbors("network.message", packet)
                            }
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //
    // SidedEnvironment

    override fun canConnect(side: EnumFacing): Boolean = side != facing()

    override fun sidedNode(side: EnumFacing): Node? = if (side != facing()) super.sidedNode(side) else null

    // ----------------------------------------------------------------------- //
    // power.Common

    @SideOnly(Side.CLIENT)
    override fun hasConnector(side: EnumFacing): Boolean = side != facing()

    override fun connector(side: EnumFacing): Connector? = if (side != facing()) sidedNode(side) as? Connector else null

    override fun energyThroughput(): Double = Settings.get.serverRackRate

    // ----------------------------------------------------------------------- //
    // Analyzable

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node>? {
        return slotAt(side, hitX, hitY, hitZ)?.let { slot ->
            components[slot]?.let { component ->
                if (component is Analyzable) component.onAnalyze(player, side, hitX, hitY, hitZ)
                else null
            }
        } ?: arrayOf(sidedNode(side))
    }

    // ----------------------------------------------------------------------- //
    // internal.Rack

    override fun indexOfMountable(mountable: RackMountable): Int = components.indexOfFirst { it == mountable }

    override fun getMountable(slot: Int): RackMountable? = components.getOrNull(slot) as? RackMountable

    override fun getMountableData(slot: Int): NBTTagCompound? = lastData[slot]

    override fun markChanged(slot: Int) {
        synchronized(hasChanged) { hasChanged[slot] = true }
        setOutputEnabled(hasRedstoneCard)
    }

    // ----------------------------------------------------------------------- //
    // StateAware

    override fun getCurrentState(): EnumSet<StateAware.State> {
        val result = EnumSet.noneOf(StateAware.State::class.java)
        components.filterNotNull().forEach { component ->
            if (component is RackMountable) {
                result.addAll(component.currentState)
            }
        }
        return result
    }

    // ----------------------------------------------------------------------- //
    // Rotatable

    override fun onRotationChanged() {
        super.onRotationChanged()
        checkRedstoneInputChanged()
    }

    // ----------------------------------------------------------------------- //
    // RedstoneAware

    override fun onRedstoneInputChanged(args: RedstoneChangedEventArgs) {
        super.onRedstoneInputChanged(args)
        components.filterNotNull().forEach { component ->
            if (component is RackMountable && component.node() != null) {
                val toLocalArgs = RedstoneChangedEventArgs(toLocal(args.side), args.oldValue, args.newValue, args.color)
                component.node().sendToNeighbors("redstone.changed", toLocalArgs)
            }
        }
    }

    // ----------------------------------------------------------------------- //
    // IInventory

    override fun getSizeInventory(): Int = 4

    override fun getInventoryStackLimit(): Int = 1

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        val driver = Driver.driverFor(stack, javaClass) ?: return false
        return driver.slot(stack) == Slot.RackMountable
    }

    override fun markDirty() {
        super.markDirty()
        if (isServer) {
            setOutputEnabled(hasRedstoneCard)
            ServerPacketSender.sendRackInventory(this)
        } else {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
        }
    }

    // ----------------------------------------------------------------------- //
    // ComponentInventory

    override fun onItemAdded(slot: Int, stack: ItemStack) {
        if (isServer) {
            for (connectable in 0 until 4) {
                nodeMapping[slot][connectable] = null
            }
            lastData[slot] = null
            hasChanged[slot] = true
        }
        super.onItemAdded(slot, stack)
    }

    override fun onItemRemoved(slot: Int, stack: ItemStack) {
        if (isServer) {
            for (connectable in 0 until 4) {
                nodeMapping[slot][connectable] = null
            }
            lastData[slot] = null
        }
        super.onItemRemoved(slot, stack)
    }

    override fun connectItemNode(node: Node) {
        // By default create a new network for mountables. They have to
        // be wired up manually (mapping is reset in onItemAdded).
        ApiNetwork.joinNewNetwork(node)
    }

    // ----------------------------------------------------------------------- //
    // TileEntity

    override fun updateEntity() {
        super.updateEntity()
        if (isServer && isConnected) {
            val connectors by lazy {
                EnumFacing.VALUES.mapNotNull { sidedNode(it) as? Connector }
            }
            components.forEachIndexed { slot, component ->
                if (component is RackMountable) {
                    if (hasChanged[slot]) {
                        hasChanged[slot] = false
                        lastData[slot] = component.data
                        ServerPacketSender.sendRackMountableData(this, slot)
                        world.notifyNeighborsOfStateChange(pos, blockType, false)
                        // These are working state dependent, so recompute them.
                        setOutputEnabled(hasRedstoneCard)
                    }

                    // Power mountables without requiring them to be connected to the outside.
                    val node = component.node()
                    if (node is Connector) {
                        var remaining = Settings.get.serverRackRate
                        for (outside in connectors) {
                            if (remaining <= 0) break
                            val received = remaining + outside.changeBuffer(-remaining)
                            val rejected = node.changeBuffer(received)
                            outside.changeBuffer(rejected)
                            remaining -= received - rejected
                        }
                    }
                }
            }

            updateComponents()
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val IsRelayEnabledTag = Settings.namespace + "isRelayEnabled"
        private val NodeMappingTag = Settings.namespace + "nodeMapping"
        private val LastDataTag = Settings.namespace + "lastData"
        private val RackDataTag = Settings.namespace + "rackData"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)

        isRelayEnabled = nbt.getBoolean(IsRelayEnabledTag)
        nbt.getTagList(NodeMappingTag, NBT.TAG_INT_ARRAY).forEachIndexed { slotIndex, tag ->
            if (tag is NBTTagIntArray && slotIndex < nodeMapping.size) {
                tag.intArray.forEachIndexed { connIndex, id ->
                    nodeMapping[slotIndex][connIndex] = if (id < 0 || id == EnumFacing.SOUTH.ordinal) null else EnumFacing.byIndex(id)
                }
            }
        }

        // Kickstart initialization.
        _isOutputEnabled = hasRedstoneCard
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)

        nbt.setBoolean(IsRelayEnabledTag, isRelayEnabled)
        nbt.setNewTagList(NodeMappingTag, nodeMapping.map { buses ->
            NBTTagIntArray(buses.map { side -> side?.ordinal ?: -1 }.toIntArray())
        })
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)

        val data = nbt.getTagList(LastDataTag, NBT.TAG_COMPOUND)
        for (i in 0 until minOf(data.tagCount(), lastData.size)) {
            lastData[i] = data.getCompoundTagAt(i)
        }
        load(nbt.getCompoundTag(RackDataTag))
        connectComponents()
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)

        val data = lastData.map { tag -> tag ?: NBTTagCompound() }
        nbt.setNewTagList(LastDataTag, data)
        nbt.setNewCompoundTag(RackDataTag) { save(it) }
    }

    // ----------------------------------------------------------------------- //

    fun slotAt(side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Int? {
        return if (side == facing()) {
            val globalY = (hitY * 16).toInt() // [0, 15]
            val l = 2
            val h = 14
            val slot = ((15 - globalY) - l) * sizeInventory / (h - l)
            Math.max(0, Math.min(sizeInventory - 1, slot))
        } else null
    }

    fun isWorking(mountable: RackMountable): Boolean = mountable.currentState.contains(StateAware.State.IsWorking)

    val hasRedstoneCard: Boolean get() = components.any { component ->
        if (component is EnvironmentHost && component is RackMountable && component is IInventory && isWorking(component)) {
            component.exists { stack -> DriverRedstoneCard.worksWith(stack, component.javaClass) }
        } else false
    }
}
