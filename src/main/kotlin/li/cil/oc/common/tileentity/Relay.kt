package li.cil.oc.common.tileentity

import com.google.common.base.Charsets
import dan200.computercraft.api.peripheral.IComputerAccess
import li.cil.oc.Constants
import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Packet
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.network.WirelessEndpoint
import li.cil.oc.common.InventorySlots
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.Memory
import li.cil.oc.integration.Mods
import li.cil.oc.integration.opencomputers.DriverLinkedCard
import li.cil.oc.server.PacketSender
import li.cil.oc.server.network.QuantumNetwork
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import li.cil.oc.common.tileentity.traits.Hub as TraitHub
import li.cil.oc.common.tileentity.traits.ComponentInventory as TraitComponentInventory
import li.cil.oc.common.tileentity.traits.PowerAcceptor as TraitPowerAcceptor

class Relay : TileEntityBase(), TraitHub, TraitComponentInventory, TraitPowerAcceptor, Analyzable, WirelessEndpoint, QuantumNetwork.QuantumNode {
    val WirelessNetworkCardTier1: ItemInfo by lazy { ApiItems.get(Constants.ItemName.WirelessNetworkCardTier1) }
    val WirelessNetworkCardTier2: ItemInfo by lazy { ApiItems.get(Constants.ItemName.WirelessNetworkCardTier2) }
    val LinkedCard: ItemInfo by lazy { ApiItems.get(Constants.ItemName.LinkedCard) }


    @JvmField
    var strength: Double = maxWirelessRange

    @JvmField
    var isRepeater = true

    @JvmField
    var wirelessTier = -1

    val isWirelessEnabled: Boolean get() = wirelessTier >= Tier.One

    val maxWirelessRange: Double get() = if (wirelessTier == Tier.One || wirelessTier == Tier.Two)
        Settings.get.maxWirelessRange[wirelessTier] else 0.0

    val wirelessCostPerRange: Double get() = if (wirelessTier == Tier.One || wirelessTier == Tier.Two)
        Settings.get.wirelessCostPerRange[wirelessTier] else 0.0

    @JvmField
    var isLinkedEnabled = false

    @JvmField
    var tunnel = "creative"

    @JvmField
    val componentNodes: Array<Component> = Array(6) {
        ApiNetwork.newNode(this, Visibility.Network)
            .withComponent("relay")
            .create()
    }

    @JvmField
    val openPorts = mutableMapOf<Any, MutableSet<Int>>()

    @JvmField
    var lastMessage = 0L

    fun onSwitchActivity() {
        val now = System.currentTimeMillis()
        if (now - lastMessage >= (relayDelay - 1) * 50) {
            lastMessage = now
            PacketSender.sendSwitchActivity(this)
        }
    }

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun hasConnector(side: EnumFacing): Boolean = true

    override fun connector(side: EnumFacing): Connector? = when (val node = sidedNode(side)) {
        is Connector -> node
        else -> null
    }

    override fun energyThroughput(): Double = Settings.get.accessPointRate

    // ----------------------------------------------------------------------- //

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node>? {
        return if (isWirelessEnabled) {
            player.sendMessage(Localization.Analyzer.WirelessStrength(strength))
            arrayOf(componentNodes[side.index])
        } else null
    }

    // ----------------------------------------------------------------------- //

    @Callback(direct = true, doc = """function():number -- Get the signal strength (range) used when relaying messages.""")
    fun getStrength(context: Context, args: Arguments): Array<Any?> = synchronized(this) { result(strength) }

    @Callback(doc = """function(strength:number):number -- Set the signal strength (range) used when relaying messages.""")
    fun setStrength(context: Context, args: Arguments): Array<Any?> = synchronized(this) {
        strength = Math.max(0.0, Math.min(args.checkDouble(0), maxWirelessRange))
        result(strength)
    }

    @Callback(direct = true, doc = """function():boolean -- Get whether the access point currently acts as a repeater (resend received wireless packets wirelessly).""")
    fun isRepeater(context: Context, args: Arguments): Array<Any?> = synchronized(this) { result(isRepeater) }

    @Callback(doc = """function(enabled:boolean):boolean -- Set whether the access point should act as a repeater.""")
    fun setRepeater(context: Context, args: Arguments): Array<Any?> = synchronized(this) {
        isRepeater = args.checkBoolean(0)
        result(isRepeater)
    }

    // ----------------------------------------------------------------------- //

    protected fun queueMessage(source: String, destination: String?, port: Int, answerPort: Int, args: Array<Any>) {
        for (computer in computers.map { it as IComputerAccess }) {
            val address = "cc${computer.id}_${computer.attachmentName}"
            if (source != address && (destination == null || destination == address) && openPorts[computer]?.contains(port) == true) {
                val eventArgs = mutableListOf<Any>(computer.attachmentName, port, answerPort)
                args.forEach { arg ->
                    eventArgs.add(when (arg) {
                        is ByteArray -> String(arg, Charsets.UTF_8)
                        else -> arg
                    })
                }
                computer.queueEvent("modem_message", eventArgs.toTypedArray())
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun receivePacket(packet: Packet, source: WirelessEndpoint) {
        if (isWirelessEnabled) {
            tryEnqueuePacket(null, packet)
        }
    }

    override fun receivePacket(packet: Packet) {
        if (isLinkedEnabled) {
            tryEnqueuePacket(null, packet)
        }
    }

    @JvmField
    val computers = mutableListOf<Any>()

    override fun tryEnqueuePacket(sourceSide: EnumFacing?, packet: Packet): Boolean {
        if (Mods.ComputerCraft.isModAvailable) {
            val firstData = packet.data().firstOrNull()
            when (firstData) {
                is java.lang.Double -> queueMessage(packet.source(), packet.destination(), packet.port(), firstData.toInt(), packet.data().drop(1).toTypedArray())
                else -> queueMessage(packet.source(), packet.destination(), packet.port(), -1, packet.data())
            }
        }
        return super.tryEnqueuePacket(sourceSide, packet)
    }

    override fun relayPacket(sourceSide: EnumFacing?, packet: Packet) {
        super.relayPacket(sourceSide, packet)

        val tryChangeBuffer: (Double) -> Boolean = if (sourceSide != null) {
            { amount: Double -> (plugs[sourceSide.ordinal].node as Connector).tryChangeBuffer(amount) }
        } else {
            { amount: Double -> plugs.any { (it.node as Connector).tryChangeBuffer(amount) } }
        }

        if (isWirelessEnabled && strength > 0 && (sourceSide != null || isRepeater)) {
            val cost = wirelessCostPerRange
            if (tryChangeBuffer(-strength * cost)) {
                ApiNetwork.sendWirelessPacket(this, strength, packet)
            }
        }

        if (isLinkedEnabled && sourceSide != null) {
            val cost = packet.size() / 32.0 + wirelessCostPerRange * maxWirelessRange * 5
            if (tryChangeBuffer(-cost)) {
                val endpoints = QuantumNetwork.getEndpoints(tunnel).filter { it != this }
                for (endpoint in endpoints) {
                    endpoint.receivePacket(packet)
                }
            }
        }

        onSwitchActivity()
    }

    // ----------------------------------------------------------------------- //

    override fun createNode(plug: Plug): Connector = ApiNetwork.newNode(plug, Visibility.Network)
        .withConnector(Math.round(Settings.get.bufferAccessPoint).toDouble())
        .create()

    override fun onPlugConnect(plug: Plug, node: Node) {
        super.onPlugConnect(plug, node)
        if (node == plug.node) {
            ApiNetwork.joinWirelessNetwork(this)
        }
        if (plug.isPrimary)
            plug.node.connect(componentNodes[plug.side.ordinal])
        else
            componentNodes[plug.side.ordinal].remove()
    }

    override fun onPlugDisconnect(plug: Plug, node: Node) {
        super.onPlugDisconnect(plug, node)
        if (node == plug.node) {
            ApiNetwork.leaveWirelessNetwork(this)
        }
        if (plug.isPrimary && node != plug.node)
            plug.node.connect(componentNodes[plug.side.ordinal])
        else
            componentNodes[plug.side.ordinal].remove()
    }

    // ----------------------------------------------------------------------- //

    override fun onItemAdded(slot: Int, stack: ItemStack) {
        super.onItemAdded(slot, stack)
        updateLimits(slot, stack)
    }

    private fun updateLimits(slot: Int, stack: ItemStack) {
        val driver = Driver.driverFor(stack, javaClass)
        when {
            driver != null && driver.slot(stack) == Slot.CPU -> {
                relayDelay = Math.max(1, relayBaseDelay - ((driver.tier(stack) + 1) * relayDelayPerUpgrade).toInt())
            }
            driver != null && driver.slot(stack) == Slot.Memory -> {
                relayAmount = Math.max(1, relayBaseAmount + (Delegator.subItem(stack)?.let { subItem ->
                    if (subItem is Memory) (subItem.tier + 1) * relayAmountPerUpgrade
                    else (driver.tier(stack) + 1) * (relayAmountPerUpgrade * 2)
                } ?: (driver.tier(stack) + 1) * (relayAmountPerUpgrade * 2)))
            }
            driver != null && driver.slot(stack) == Slot.HDD -> {
                maxQueueSize = Math.max(1, queueBaseSize + (driver.tier(stack) + 1) * queueSizePerUpgrade)
            }
            driver != null && driver.slot(stack) == Slot.Card -> {
                val descriptor = ApiItems.get(stack)
                if (descriptor == WirelessNetworkCardTier1 || descriptor == WirelessNetworkCardTier2) {
                    wirelessTier = if (descriptor == WirelessNetworkCardTier1) Tier.One else Tier.Two
                }
                if (descriptor == LinkedCard) {
                    val data = DriverLinkedCard.dataTag(stack)
                    if (data.hasKey(Settings.namespace + "tunnel")) {
                        tunnel = data.getString(Settings.namespace + "tunnel")
                        isLinkedEnabled = true
                        QuantumNetwork.add(this)
                    }
                }
            }
        }
    }

    override fun onItemRemoved(slot: Int, stack: ItemStack) {
        super.onItemRemoved(slot, stack)
        val driver = Driver.driverFor(stack, javaClass)
        when {
            driver != null && driver.slot(stack) == Slot.CPU -> relayDelay = relayBaseDelay
            driver != null && driver.slot(stack) == Slot.Memory -> relayAmount = relayBaseAmount
            driver != null && driver.slot(stack) == Slot.HDD -> maxQueueSize = queueBaseSize
            driver != null && driver.slot(stack) == Slot.Card -> {
                wirelessTier = -1
                isLinkedEnabled = false
                QuantumNetwork.remove(this)
            }
        }
    }

    override fun getSizeInventory(): Int = InventorySlots.relay.size

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        val driver = Driver.driverFor(stack, javaClass) ?: return false
        val provided = InventorySlots.relay[slot]
        val tierSatisfied = driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
        val cardTypeSatisfied = if (provided.slot == Slot.Card) {
            ApiItems.get(stack) == WirelessNetworkCardTier1 ||
                ApiItems.get(stack) == WirelessNetworkCardTier2 ||
                ApiItems.get(stack) == LinkedCard
        } else true
        return tierSatisfied && cardTypeSatisfied
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val StrengthTag = Settings.namespace + "strength"
        private val IsRepeaterTag = Settings.namespace + "isRepeater"
        private val ComponentNodesTag = Settings.namespace + "componentNodes"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        for (slot in items.indices) {
            if (!items[slot].isEmpty) {
                updateLimits(slot, items[slot])
            }
        }

        if (nbt.hasKey(StrengthTag)) {
            strength = nbt.getDouble(StrengthTag).coerceIn(0.0, maxWirelessRange)
        }
        if (nbt.hasKey(IsRepeaterTag)) {
            isRepeater = nbt.getBoolean(IsRepeaterTag)
        }
        nbt.getTagList(ComponentNodesTag, NBT.TAG_COMPOUND).forEachIndexed { index, tag ->
            if (tag is NBTTagCompound && index < componentNodes.size) {
                componentNodes[index].load(tag)
            }
        }
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        nbt.setDouble(StrengthTag, strength)
        nbt.setBoolean(IsRepeaterTag, isRepeater)
        nbt.setNewTagList(ComponentNodesTag, componentNodes.map { node ->
            val tag = NBTTagCompound()
            node?.save(tag)
            tag
        })
    }
}
