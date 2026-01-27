package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ComponentConnector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.util.StateAware
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.tileentity.traits.Inventory
import li.cil.oc.common.tileentity.traits.Rotatable
import li.cil.oc.common.tileentity.traits.isClient
import li.cil.oc.server.component.result
import li.cil.oc.util.notEmpty
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.inventory.ISidedInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.text.ITextComponent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.*
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.common.tileentity.traits.Inventory as TraitInventory
import li.cil.oc.common.tileentity.traits.Rotatable as TraitRotatable
import li.cil.oc.common.tileentity.traits.StateAware as TraitStateAware
import li.cil.oc.common.tileentity.traits.Tickable as TraitTickable
import li.cil.oc.server.PacketSender as ServerPacketSender

class Printer : TileEntityBase.TEEnvironmentBase(), TraitInventory, TraitRotatable, SidedEnvironment, TraitStateAware, TraitTickable, ISidedInventory, DeviceInfo {
    @JvmField
    val node: ComponentConnector = ApiNetwork.newNode(this, Visibility.Network)!!
        .withComponent("printer3d")
        .withConnector(Settings.get.bufferConverter)
        .create()
    override fun node(): Node = node

    override val inventoryDelegate: Inventory.Delegate = register(Inventory::Delegate)
    override val rotatableDelegate: Rotatable.RotatableDelegate = register(Rotatable::RotatableDelegate)

    @JvmField
    val maxAmountMaterial = 256000
    @JvmField
    var amountMaterial = 0
    @JvmField
    val maxAmountInk = 100000
    @JvmField
    var amountInk = 0

    @JvmField
    var data = PrintData()
    @JvmField
    var isActive = false
    @JvmField
    var limit = 0
    @JvmField
    var output: ItemStack? = null
    @JvmField
    var totalRequiredEnergy = 0.0
    @JvmField
    var requiredEnergy = 0.0

    val slotMaterial = 0
    val slotInk = 1
    val slotOutput = 2

    private val deviceInfo_: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Printer,
            DeviceAttribute.Description to "3D Printer",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Omni-Materializer T6.1"
        )
    }

    override fun getDeviceInfo(): Map<String, String> = deviceInfo_

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = side != EnumFacing.UP

    override fun sidedNode(side: EnumFacing): ComponentConnector? = if (side != EnumFacing.UP) node else null

    override fun getCurrentState(): EnumSet<StateAware.State> {
        return when {
            isPrinting -> EnumSet.of(StateAware.State.IsWorking)
            canPrint -> EnumSet.of(StateAware.State.CanWork)
            else -> EnumSet.noneOf(StateAware.State::class.java)
        }
    }

    // ----------------------------------------------------------------------- //

    val canPrint: Boolean get() = data.stateOff.isNotEmpty() && data.stateOff.size <= Settings.get.maxPrintComplexity && data.stateOn.size <= Settings.get.maxPrintComplexity

    val isPrinting: Boolean get() = output != null

    val progress: Double get() = (1 - requiredEnergy / totalRequiredEnergy) * 100

    val timeRemaining: Int get() = (requiredEnergy / Settings.get.assemblerTickAmount / 20).toInt()

    // ----------------------------------------------------------------------- //

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function() -- Resets the configuration of the printer and stop printing (current job will finish).""")
    fun reset(context: Context, args: Arguments): Array<Any?>? {
        data = PrintData()
        isActive = false // Needs committing.
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(value:string) -- Set a label for the block being printed.""")
    fun setLabel(context: Context, args: Arguments): Array<Any?>? {
        data.label = args.optString(0, null)?.take(24)?.let { if (it.isEmpty()) null else it }
        isActive = false // Needs committing.
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function():string -- Get the current label for the block being printed.""")
    fun getLabel(context: Context, args: Arguments): Array<Any?> = result(data.label)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(value:string) -- Set a tooltip for the block being printed.""")
    fun setTooltip(context: Context, args: Arguments): Array<Any?>? {
        data.tooltip = args.optString(0, null)?.take(128)?.let { if (it.isEmpty()) null else it }
        isActive = false // Needs committing.
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function():string -- Get the current tooltip for the block being printed.""")
    fun getTooltip(context: Context, args: Arguments): Array<Any?> = result(data.tooltip)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(value:number) -- Set what light level the printed block should have.""")
    fun setLightLevel(context: Context, args: Arguments): Array<Any?>? {
        data.lightLevel = args.checkInteger(0).coerceIn(0, Settings.get.maxPrintLightLevel)
        isActive = false // Needs committing.
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function():number -- Get which light level the printed block should have.""")
    fun getLightLevel(context: Context, args: Arguments): Array<Any?> = result(data.lightLevel)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(value:boolean or number) -- Set whether the printed block should emit redstone when in its active state.""")
    fun setRedstoneEmitter(context: Context, args: Arguments): Array<Any?>? {
        data.redstoneLevel = if (args.isBoolean(0)) {
            if (args.checkBoolean(0)) 15 else 0
        } else {
            args.checkInteger(0).coerceIn(0, 15)
        }
        isActive = false // Needs committing.
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function():boolean, number -- Get whether the printed block should emit redstone when in its active state.""")
    fun isRedstoneEmitter(context: Context, args: Arguments): Array<Any?> = result(data.emitRedstone, data.redstoneLevel)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(value:boolean) -- Set whether the printed block should automatically return to its off state.""")
    fun setButtonMode(context: Context, args: Arguments): Array<Any?>? {
        data.isButtonMode = args.checkBoolean(0)
        isActive = false // Needs committing.
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function():boolean -- Get whether the printed block should automatically return to its off state.""")
    fun isButtonMode(context: Context, args: Arguments): Array<Any?> = result(data.isButtonMode)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(collideOff:boolean, collideOn:boolean) -- Set whether the printed block should be collidable or not.""")
    fun setCollidable(context: Context, args: Arguments): Array<Any?>? {
        val collideOff = args.checkBoolean(0)
        val collideOn = args.checkBoolean(1)
        data.noclipOff = !collideOff
        data.noclipOn = !collideOn
        return null
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function():boolean, boolean -- Get whether the printed block should be collidable or not.""")
    fun isCollidable(context: Context, args: Arguments): Array<Any?> = result(!data.noclipOff, !data.noclipOn)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(minX:number, minY:number, minZ:number, maxX:number, maxY:number, maxZ:number, texture:string[, state:boolean=false][,tint:number]) -- Adds a shape to the printers configuration, optionally specifying whether it is for the off or on state.""")
    fun addShape(context: Context, args: Arguments): Array<Any?> {
        if (data.stateOff.size > Settings.get.maxPrintComplexity || data.stateOn.size > Settings.get.maxPrintComplexity) {
            return result(Unit, "model too complex")
        }
        val minX = args.checkInteger(0).coerceIn(0, 16) / 16f
        val minY = args.checkInteger(1).coerceIn(0, 16) / 16f
        val minZ = (16 - args.checkInteger(2).coerceIn(0, 16)) / 16f
        val maxX = args.checkInteger(3).coerceIn(0, 16) / 16f
        val maxY = args.checkInteger(4).coerceIn(0, 16) / 16f
        val maxZ = (16 - args.checkInteger(5).coerceIn(0, 16)) / 16f
        val texture = args.checkString(6).take(64)
        val state = if (args.isBoolean(7)) args.checkBoolean(7) else false
        val tint = when {
            args.isInteger(7) -> args.checkInteger(7)
            args.isInteger(8) -> args.checkInteger(8)
            else -> null
        }

        if (minX == maxX) throw IllegalArgumentException("empty block")
        if (minY == maxY) throw IllegalArgumentException("empty block")
        if (minZ == maxZ) throw IllegalArgumentException("empty block")

        val list = if (state) data.stateOn else data.stateOff
        list.add(PrintData.Shape(
            net.minecraft.util.math.AxisAlignedBB(
                Math.min(minX.toDouble(), maxX.toDouble()),
                Math.min(minY.toDouble(), maxY.toDouble()),
                Math.min(minZ.toDouble(), maxZ.toDouble()),
                Math.max(maxX.toDouble(), minX.toDouble()),
                Math.max(maxY.toDouble(), minY.toDouble()),
                Math.max(maxZ.toDouble(), minZ.toDouble())
            ),
            texture, tint
        ))
        isActive = false // Needs committing.

        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)

        return result(true)
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function():number -- Get the number of shapes in the current configuration.""")
    fun getShapeCount(context: Context, args: Arguments): Array<Any?> = result(data.stateOff.size, data.stateOn.size)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function():number -- Get the maximum allowed number of shapes.""")
    fun getMaxShapeCount(context: Context, args: Arguments): Array<Any?> = result(Settings.get.maxPrintComplexity)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function([count:number]):boolean -- Commit and begin printing the current configuration.""")
    fun commit(context: Context, args: Arguments): Array<Any?> {
        if (!canPrint) {
            return result(Unit, "model invalid")
        }
        limit = args.optDouble(0, 1.0).coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt()
        isActive = limit > 0
        return result(true)
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(): string, number or boolean -- The current state of the printer, `busy' or `idle', followed by the progress or model validity, respectively.""")
    fun status(context: Context, args: Arguments): Array<Any?> {
        return when {
            isPrinting -> result("busy", progress)
            canPrint -> result("idle", true)
            else -> result("idle", false)
        }
    }

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        super.updateEntity()

        if (isClient) {
            return
        }

        fun canMergeOutput(): Boolean {
            val presentStack = getStackInSlot(slotOutput)
            val outputStack = data.createItemStack()
            return presentStack.isEmpty || (presentStack.isItemEqual(outputStack) && ItemStack.areItemStackTagsEqual(presentStack, outputStack))
        }

        if (isActive && output != null && canMergeOutput()) {
            val costs = PrintData.computeCosts(data)
            if (costs != null) {
                val (materialRequired, inkRequired) = costs
                totalRequiredEnergy = Settings.get.printCost
                requiredEnergy = totalRequiredEnergy

                if (amountMaterial >= materialRequired && amountInk >= inkRequired) {
                    amountMaterial -= materialRequired
                    amountInk -= inkRequired
                    limit -= 1
                    output = data.createItemStack().notEmpty()
                    if (limit < 1) isActive = false
                    ServerPacketSender.sendPrinting(this, true)
                }
            } else {
                isActive = false
                data = PrintData()
            }
        }

        if (output != null) {
            val want = requiredEnergy.coerceIn(1.0 .. Settings.get.printerTickAmount)
            val have = want + (if (Settings.get.ignorePower) 0.0 else node.changeBuffer(-want))
            requiredEnergy -= have
            if (requiredEnergy <= 0) {
                val result = getStackInSlot(slotOutput)
                if (result.isEmpty) {
                    setInventorySlotContents(slotOutput, output!!)
                } else if (result.count < result.maxStackSize && canMergeOutput()) {
                    result.grow(1)
                    markDirty()
                } else {
                    return
                }
                requiredEnergy = 0.0
                output = null
            }
            ServerPacketSender.sendPrinting(this, have > 0.5 && output != null)
        }

        val inputValue = PrintData.materialValue(getStackInSlot(slotMaterial))
        if (inputValue > 0 && maxAmountMaterial - amountMaterial >= inputValue) {
            val material = decrStackSize(slotMaterial, 1)
            if (!material.isEmpty) {
                amountMaterial += inputValue
            }
        }

        val inkValue = PrintData.inkValue(getStackInSlot(slotInk))
        if (inkValue > 0 && maxAmountInk - amountInk >= inkValue) {
            val material = decrStackSize(slotInk, 1)
            if (!material.isEmpty) {
                amountInk += inkValue
                if (material.item.hasContainerItem(material)) {
                    setInventorySlotContents(slotInk, material.item.getContainerItem(material))
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val AmountMaterialTag = Settings.namespace + "amountMaterial"
        private val AmountInkTag = Settings.namespace + "amountInk"
        private val DataTag = Settings.namespace + "data"
        private val IsActiveTag = Settings.namespace + "active"
        private val LimitTag = Settings.namespace + "limit"
        private val OutputTag = Settings.namespace + "output"
        private val TotalTag = Settings.namespace + "total"
        private val RemainingTag = Settings.namespace + "remaining"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        amountMaterial = nbt.getInteger(AmountMaterialTag)
        amountInk = nbt.getInteger(AmountInkTag)
        data.load(nbt.getCompoundTag(DataTag))
        isActive = nbt.getBoolean(IsActiveTag)
        limit = nbt.getInteger(LimitTag)
        output = if (nbt.hasKey(OutputTag)) {
            ItemStack(nbt.getCompoundTag(OutputTag))
        } else {
            null
        }
        totalRequiredEnergy = nbt.getDouble(TotalTag)
        requiredEnergy = nbt.getDouble(RemainingTag)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        nbt.setInteger(AmountMaterialTag, amountMaterial)
        nbt.setInteger(AmountInkTag, amountInk)
        nbt.setNewCompoundTag(DataTag, data::save)
        nbt.setBoolean(IsActiveTag, isActive)
        nbt.setInteger(LimitTag, limit)
        output?.let { stack -> nbt.setNewCompoundTag(OutputTag, stack::writeToNBT) }
        nbt.setDouble(TotalTag, totalRequiredEnergy)
        nbt.setDouble(RemainingTag, requiredEnergy)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        data.load(nbt.getCompoundTag(DataTag))
        requiredEnergy = nbt.getDouble(RemainingTag)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setNewCompoundTag(DataTag) { data.save(it) }
        nbt.setDouble(RemainingTag, requiredEnergy)
    }

    // ----------------------------------------------------------------------- //

    override fun getSizeInventory(): Int = 3

    override fun getDisplayName(): ITextComponent = super<Inventory>.getDisplayName()

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean = when (slot) {
        slotMaterial -> PrintData.materialValue(stack) > 0
        slotInk -> PrintData.inkValue(stack) > 0
        else -> false
    }

    // ----------------------------------------------------------------------- //

    override fun getSlotsForFace(side: EnumFacing): IntArray = intArrayOf(slotMaterial, slotInk, slotOutput)

    override fun canExtractItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean = !isItemValidForSlot(slot, stack)

    override fun canInsertItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean = slot != slotOutput
}
