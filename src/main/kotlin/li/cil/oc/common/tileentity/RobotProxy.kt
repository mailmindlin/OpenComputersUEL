package li.cil.oc.common.tileentity

import java.util.UUID
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.internal.MultiTank
import li.cil.oc.api.internal.Robot as InternalRobot
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network.*
import li.cil.oc.common.inventory.InventoryProxy
import li.cil.oc.common.tileentity.traits.PowerInformation
import li.cil.oc.common.tileentity.traits.RedstoneAware
import li.cil.oc.server.agent.Player
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.server.component.result
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.fluids.capability.CapabilityFluidHandler
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.ISidedInventory
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.IFluidTank
import net.minecraftforge.fluids.capability.IFluidTankProperties
import li.cil.oc.common.tileentity.traits.Computer as TraitComputer
import li.cil.oc.common.tileentity.traits.PowerInformation as TraitPowerInformation
import li.cil.oc.common.tileentity.traits.RotatableTile as TraitRotatableTile

class RobotProxy(val robot: Robot = Robot()) : TraitComputer(), TraitPowerInformation, TraitRotatableTile, ISidedInventory, IFluidHandler, InternalRobot {
    override val powerDelegate: PowerInformation.Delegate = register(PowerInformation::Delegate)

    // ----------------------------------------------------------------------- //

    override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)
            return capability.cast(this as T)
        return super.getCapability(capability, facing)
    }

    private val node: Component = ApiNetwork.newNode(this, Visibility.Network)
        .withComponent("robot", Visibility.Neighbors)
        .create()
    override fun node(): Node? = node

    override fun machine(): Machine = robot.machine()

    override fun tier(): Int = robot.tier()

    override fun equipmentInventory(): InventoryProxy = robot.equipmentInventory

    override fun mainInventory(): InventoryProxy = robot.mainInventory

    override fun tank(): MultiTank = robot.tank()

    override fun selectedSlot(): Int = robot.selectedSlot()

    override fun setSelectedSlot(index: Int) = robot.setSelectedSlot(index)

    override fun selectedTank(): Int = robot.selectedTank()

    override fun setSelectedTank(index: Int) = robot.setSelectedTank(index)

    override fun player(): Player = robot.player()

    override fun name(): String = robot.name()

    override fun setName(name: String) = robot.setName(name)

    override fun ownerName(): String = robot.ownerName()

    override fun ownerUUID(): UUID = robot.ownerUUID()

    // ----------------------------------------------------------------------- //

    override fun connectComponents() {}
    override fun disconnectComponents() {}
    override fun isRunning(): Boolean = robot.isRunning

    override fun setRunning(value: Boolean) = robot.setRunning(value)

    override fun shouldAnimate(): Boolean = robot.shouldAnimate()

    // ----------------------------------------------------------------------- //

    override fun componentCount(): Int = robot.componentCount()

    override fun getComponentInSlot(index: Int): ManagedEnvironment = robot.getComponentInSlot(index)

    override fun synchronizeSlot(slot: Int) = robot.synchronizeSlot(slot)

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function():boolean -- Starts the robot. Returns true if the state changed.""")
    fun start(context: Context, args: Arguments): Array<Any?> =
        result(!machine().isPaused && machine().start())

    @Callback(doc = """function():boolean -- Stops the robot. Returns true if the state changed.""")
    fun stop(context: Context, args: Arguments): Array<Any?> =
        result(machine().stop())

    @Callback(direct = true, doc = """function():boolean -- Returns whether the robot is running.""")
    fun isRunning(context: Context, args: Arguments): Array<Any?> =
        result(machine().isRunning)

    @Callback(doc = "function(name: string):string -- Sets a new name and returns the old name. Robot must not be running")
    fun setName(context: Context, args: Arguments): Array<Any?> {
        val oldName = robot.name()
        val newName: String = args.checkString(0)
        if (machine().isRunning) return result(Unit, "is running")
        setName(newName)
        ServerPacketSender.sendRobotNameChange(robot)
        return result(oldName)
    }

    @Callback(doc = "function():string -- Returns the robot name.")
    fun getName(context: Context, args: Arguments): Array<Any?> = result(robot.name())

    override fun onMessage(message: Message) {
        super.onMessage(message)
        if (message.name() == "network.message" && message.source() != this.node) {
            when (val data = message.data()) {
                is Array<*> -> if (data.isNotEmpty() && data[0] is Packet) {
                    robot.node().sendToReachable(message.name(), data[0])
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        robot.updateEntity()
    }

    override fun validate() {
        super.validate()
        val firstProxy = robot.proxyRaw == null
        robot.proxyRaw = this
        robot.setWorld(world)
        robot.setPos(pos)
        if (firstProxy) {
            robot.validate()
        }
        if (isServer) {
            // Use the same address we use internally on the outside.
            val nbt = NBTTagCompound()
            nbt.setString("address", robot.node().address())
            node.load(nbt)
        }
    }

    override fun dispose() {
        super.dispose()
        if (robot.proxy == this) {
            robot.dispose()
        }
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        robot.info.load(nbt)
        super.readFromNBTForServer(nbt)
        robot.readFromNBTForServer(nbt)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        robot.writeToNBTForServer(nbt)
    }

    override fun save(nbt: NBTTagCompound) = robot.save(nbt)

    override fun load(nbt: NBTTagCompound) = robot.load(nbt)

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) = robot.readFromNBTForClient(nbt)

    override fun writeToNBTForClient(nbt: NBTTagCompound) = robot.writeToNBTForClient(nbt)

    override fun getMaxRenderDistanceSquared(): Double = robot.getMaxRenderDistanceSquared()

    override fun getRenderBoundingBox(): AxisAlignedBB = robot.getRenderBoundingBox()

    override fun shouldRenderInPass(pass: Int): Boolean = robot.shouldRenderInPass(pass)

    override fun markDirty() = robot.markDirty()

    // ----------------------------------------------------------------------- //

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node> =
        robot.onAnalyze(player, side, hitX, hitY, hitZ)

    // ----------------------------------------------------------------------- //

    override val _input: IntArray get() = robot._input

    override val _output: IntArray get() = robot._output

    override val _bundledInput: Array<IntArray> get() = robot._bundledInput

    override val _rednetInput: Array<IntArray> get() = robot._rednetInput

    override val _bundledOutput: Array<IntArray> get() = robot._bundledOutput

    override fun isOutputEnabled(): Boolean = robot.isOutputEnabled()

    override fun setOutputEnabled(value: Boolean): RedstoneAware = robot.setOutputEnabled(value)

    override fun checkRedstoneInputChanged() = robot.checkRedstoneInputChanged()

    /* TORO RedLogic
    @Optional.Method(modid = Mods.IDs.RedLogic)
    override fun connects(wire: IWire, blockFace: Int, fromDirection: Int) = robot.connects(wire, blockFace, fromDirection)

    @Optional.Method(modid = Mods.IDs.RedLogic)
    override fun connectsAroundCorner(wire: IWire, blockFace: Int, fromDirection: Int) = robot.connectsAroundCorner(wire, blockFace, fromDirection)

    @Optional.Method(modid = Mods.IDs.RedLogic)
    override fun getBundledCableStrength(blockFace: Int, toDirection: Int) = robot.getBundledCableStrength(blockFace, toDirection)

    @Optional.Method(modid = Mods.IDs.RedLogic)
    override fun getEmittedSignalStrength(blockFace: Int, toDirection: Int) = robot.getEmittedSignalStrength(blockFace, toDirection)

    @Optional.Method(modid = Mods.IDs.RedLogic)
    override fun onBundledInputChanged() = robot.onBundledInputChanged()

    @Optional.Method(modid = Mods.IDs.RedLogic)
    override fun onRedstoneInputChanged() = robot.onRedstoneInputChanged()
    */

    // ----------------------------------------------------------------------- //

    override fun pitch(): EnumFacing = robot.pitch()

    override fun pitch_=(value: EnumFacing) {
        robot.pitch_=(value)
    }

    override fun yaw(): EnumFacing = robot.yaw()

    override fun yaw_=(value: EnumFacing) {
        robot.yaw_=(value)
    }

    override fun setFromEntityPitchAndYaw(entity: Entity): Boolean = robot.setFromEntityPitchAndYaw(entity)

    override fun setFromFacing(value: EnumFacing): Boolean = robot.setFromFacing(value)

    override fun invertRotation(): Boolean = robot.invertRotation()

    override fun facing(): EnumFacing = robot.facing()

    override fun rotate(axis: EnumFacing): Boolean = robot.rotate(axis)

    override fun toLocal(value: EnumFacing): EnumFacing = robot.toLocal(value)

    override fun toGlobal(value: EnumFacing): EnumFacing = robot.toGlobal(value)

    // ----------------------------------------------------------------------- //

    override fun getStackInSlot(i: Int): ItemStack = robot.getStackInSlot(i)

    override fun decrStackSize(slot: Int, amount: Int): ItemStack = robot.decrStackSize(slot, amount)

    override fun setInventorySlotContents(slot: Int, stack: ItemStack) = robot.setInventorySlotContents(slot, stack)

    override fun removeStackFromSlot(slot: Int): ItemStack = robot.removeStackFromSlot(slot)

    override fun openInventory(player: EntityPlayer) = robot.openInventory(player)

    override fun closeInventory(player: EntityPlayer) = robot.closeInventory(player)

    override fun hasCustomName(): Boolean = robot.hasCustomName()

    override fun isUsableByPlayer(player: EntityPlayer): Boolean = robot.isUsableByPlayer(player)

    override fun dropSlot(slot: Int, count: Int, direction: EnumFacing?): Boolean = robot.dropSlot(slot, count, direction)

    override fun dropAllSlots() = robot.dropAllSlots()

    override fun getInventoryStackLimit(): Int = robot.getInventoryStackLimit()

    override fun componentSlot(address: String): Int = robot.componentSlot(address)

    override fun getName(): String = robot.getName()

    override fun getSizeInventory(): Int = robot.getSizeInventory()

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean = robot.isItemValidForSlot(slot, stack)

    // ----------------------------------------------------------------------- //

    override fun canExtractItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean =
        robot.canExtractItem(slot, stack, side)

    override fun canInsertItem(slot: Int, stack: ItemStack, side: EnumFacing): Boolean =
        robot.canInsertItem(slot, stack, side)

    override fun getSlotsForFace(side: EnumFacing): IntArray = robot.getSlotsForFace(side)

    // ----------------------------------------------------------------------- //

    override fun hasRedstoneCard(): Boolean = robot.hasRedstoneCard()

    // ----------------------------------------------------------------------- //

    override fun globalBuffer(): Double = robot.globalBuffer()

    override fun globalBuffer_=(value: Double) {
        robot.globalBuffer_=(value)
    }

    override fun globalBufferSize(): Double = robot.globalBufferSize()

    override fun globalBufferSize_=(value: Double) {
        robot.globalBufferSize_=(value)
    }

    // ----------------------------------------------------------------------- //

    override fun fill(resource: FluidStack, doFill: Boolean): Int = robot.fill(resource, doFill)

    override fun drain(resource: FluidStack, doDrain: Boolean): FluidStack? = robot.drain(resource, doDrain)

    override fun drain(maxDrain: Int, doDrain: Boolean): FluidStack? = robot.drain(maxDrain, doDrain)

    fun canFill(fluid: Fluid): Boolean = robot.canFill(fluid)

    fun canDrain(fluid: Fluid): Boolean = robot.canDrain(fluid)

    override fun getTankProperties(): Array<IFluidTankProperties> = robot.getTankProperties()
}
