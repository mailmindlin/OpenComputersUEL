package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Microcontroller as InternalMicrocontroller
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.ComponentConnector
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.Tier
import li.cil.oc.common.item.data.MicrocontrollerData
import li.cil.oc.server.component.DeviceInfoKt
import li.cil.oc.util.ExtendedArguments._
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.StackOption
import li.cil.oc.util.StackOption._
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.ISidedInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class Microcontroller : TileEntityBase(), traits.PowerAcceptor, traits.Hub, traits.Computer, ISidedInventory, InternalMicrocontroller, DeviceInfoKt {
    @JvmField
    val info = MicrocontrollerData()

    override fun getNode(): Node? = null

    @JvmField
    val outputSides: Array<Boolean> = Array(6) { true }

    @JvmField
    val snooperNode: ComponentConnector = ApiNetwork.newNode(this, Visibility.Network)
        .withComponent("microcontroller")
        .withConnector(Settings.get.bufferMicrocontroller)
        .create()

    @JvmField
    val componentNodes: Array<Component> = Array(6) {
        ApiNetwork.newNode(this, Visibility.Network)
            .withComponent("microcontroller")
            .create()
    }

    init {
        if (machine != null) {
            (machine.node() as Connector).setLocalBufferSize(0.0)
            machine.setCostPerTick(Settings.get.microcontrollerCost)
        }
    }

    override fun tier(): Int = info.tier

    override val runSound: String? = null // Microcontrollers are silent.

    override val deviceInfo: Map<String, String> = mapOf(
        DeviceAttribute.Class to DeviceClass.System,
        DeviceAttribute.Description to "Microcontroller",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "Cubicle",
        DeviceAttribute.Capacity to sizeInventory.toString()
    )

    private inline val facing: EnumFacing get() = facing()

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = side != facing

    override fun sidedNode(side: EnumFacing): Node? = if (side != facing) super.sidedNode(side) else null

    @SideOnly(Side.CLIENT)
    override fun hasConnector(side: EnumFacing): Boolean = side != facing

    override fun connector(side: EnumFacing): Connector? = if (side != facing) snooperNode else null

    override fun energyThroughput(): Double = Settings.get.caseRate(Tier.One)

    // ----------------------------------------------------------------------- //

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node> {
        super.onAnalyze(player, side, hitX, hitY, hitZ)
        return if (side != facing)
            arrayOf(componentNodes[side.index])
        else
            arrayOf(machine.node())
    }

    // ----------------------------------------------------------------------- //

    override fun internalComponents(): Iterable<ItemStack> = info.components.asIterable()

    override fun componentSlot(address: String): Int = components.indexOfFirst { it?.node != null && it.node.address() == address }

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function():boolean -- Starts the microcontroller. Returns true if the state changed.""")
    fun start(context: Context, args: Arguments): Array<Any?> =
        result(!machine.isPaused && machine.start())

    @Callback(doc = """function():boolean -- Stops the microcontroller. Returns true if the state changed.""")
    fun stop(context: Context, args: Arguments): Array<Any?> =
        result(machine.stop())

    @Callback(direct = true, doc = """function():boolean -- Returns whether the microcontroller is running.""")
    fun isRunning(context: Context, args: Arguments): Array<Any?> =
        result(machine.isRunning)

    @Callback(direct = true, doc = """function():string -- Returns the reason the microcontroller crashed, if applicable.""")
    fun lastError(context: Context, args: Arguments): Array<Any?> =
        result(machine.lastError())

    @Callback(direct = true, doc = """function(side:number):boolean -- Get whether network messages are sent via the specified side.""")
    fun isSideOpen(context: Context, args: Arguments): Array<Any?> {
        val side = args.checkSideExcept(0, facing)
        return result(outputSides[side.ordinal])
    }

    @Callback(doc = """function(side:number, open:boolean):boolean -- Set whether network messages are sent via the specified side.""")
    fun setSideOpen(context: Context, args: Arguments): Array<Any?> {
        val side = args.checkSideExcept(0, facing)
        val oldValue = outputSides[side.ordinal]
        outputSides[side.ordinal] = args.checkBoolean(1)
        return result(oldValue)
    }

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        super.updateEntity()

        // Pump energy into the internal network.
        if (isServer && world.totalWorldTime % Settings.get.tickFrequency == 0L) {
            for (side in EnumFacing.values()) {
                if (side != facing) {
                    val node = sidedNode(side)
                    if (node is Connector) {
                        val demand = snooperNode.globalBufferSize() - snooperNode.globalBuffer()
                        val available = demand + node.changeBuffer(-demand)
                        snooperNode.changeBuffer(available)
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun connectItemNode(node: Node) {
        if (machine != null && machine.node() != null && node != null) {
            ApiNetwork.joinNewNetwork(machine.node())
            machine.node().connect(node)
        }
    }

    // ----------------------------------------------------------------------- //

    override fun createNode(plug: Plug): Node = ApiNetwork.newNode(plug, Visibility.Network)
        .withConnector()
        .create()

    override fun onPlugConnect(plug: Plug, node: Node) {
        super.onPlugConnect(plug, node)
        if (node == plug.node) {
            ApiNetwork.joinNewNetwork(machine.node())
            machine.node().connect(snooperNode)
            connectComponents()
        }
        if (plug.isPrimary)
            plug.node.connect(componentNodes[plug.side.ordinal])
        else
            componentNodes[plug.side.ordinal].remove()
    }

    override fun onPlugDisconnect(plug: Plug, node: Node) {
        super.onPlugDisconnect(plug, node)
        if (plug.isPrimary && node != plug.node)
            plug.node.connect(componentNodes[plug.side.ordinal])
        else
            componentNodes[plug.side.ordinal].remove()
        if (node == plug.node)
            disconnectComponents()
    }

    override fun onPlugMessage(plug: Plug, message: Message) {
        if (message.name() == "network.message" && message.source().network() != snooperNode.network()) {
            snooperNode.sendToReachable(message.name(), *message.data())
        }
    }

    override fun onMessage(message: Message) {
        if (message.name() == "network.message" && message.source().network() == snooperNode.network()) {
            for (side in EnumFacing.values()) {
                if (outputSides[side.ordinal] && side != facing) {
                    sidedNode(side)?.sendToReachable(message.name(), *message.data())
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val InfoTag = Settings.namespace + "info"
        private val OutputsTag = Settings.namespace + "outputs"
        private val ComponentNodesTag = Settings.namespace + "componentNodes"
        private val SnooperTag = Settings.namespace + "snooper"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        // Load info before inventory and such, to avoid initializing components
        // to empty inventory.
        info.load(nbt.getCompoundTag(InfoTag))
        nbt.getBooleanArray(OutputsTag)
        nbt.getTagList(ComponentNodesTag, NBT.TAG_COMPOUND).forEachIndexed { index, tag ->
            if (tag is NBTTagCompound && index < componentNodes.size) {
                componentNodes[index].load(tag)
            }
        }
        snooperNode.load(nbt.getCompoundTag(SnooperTag))
        super.readFromNBTForServer(nbt)
        ApiNetwork.joinNewNetwork(machine.node())
        machine.node().connect(snooperNode)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        nbt.setNewCompoundTag(InfoTag) { info.save(it) }
        nbt.setBooleanArray(OutputsTag, outputSides.toBooleanArray())
        nbt.setNewTagList(ComponentNodesTag, componentNodes.map { node ->
            val tag = NBTTagCompound()
            node?.save(tag)
            tag
        })
        nbt.setNewCompoundTag(SnooperTag) { snooperNode.save(it) }
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        info.load(nbt.getCompoundTag(InfoTag))
        super.readFromNBTForClient(nbt)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setNewCompoundTag(InfoTag) { info.save(it) }
    }

    // ----------------------------------------------------------------------- //

    override fun items(): Array<ItemStack> = info.components

    override fun updateItems(slot: Int, stack: ItemStack) {
        info.components[slot] = stack
    }

    override fun getSizeInventory(): Int = info.components.size

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean = false

    // Nope.
    override fun setInventorySlotContents(slot: Int, stack: ItemStack) {}

    // Nope.
    override fun decrStackSize(slot: Int, amount: Int): ItemStack = ItemStack.EMPTY

    // Nope.
    override fun removeStackFromSlot(slot: Int): ItemStack = ItemStack.EMPTY

    // Nope.
    override fun canExtractItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean = false

    override fun canInsertItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean = false

    override fun getSlotsForFace(side: EnumFacing): IntArray = intArrayOf()

    // For hotswapping EEPROMs.
    fun changeEEPROM(newEeprom: ItemStack): StackOption {
        val oldEepromIndex = info.components.indexOfFirst { ApiItems.get(it) == ApiItems.get(Constants.ItemName.EEPROM) }
        return if (oldEepromIndex >= 0) {
            val oldEeprom = info.components[oldEepromIndex]
            super.setInventorySlotContents(oldEepromIndex, newEeprom)
            SomeStack(oldEeprom)
        } else {
            assert(info.components[sizeInventory - 1].isEmpty)
            super.setInventorySlotContents(sizeInventory - 1, newEeprom)
            EmptyStack
        }
    }
}
