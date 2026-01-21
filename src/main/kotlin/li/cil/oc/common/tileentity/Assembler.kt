package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.util.StateAware
import li.cil.oc.common.template.AssemblerTemplates
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.StackOption
import li.cil.oc.util.StackOption.EmptyStack
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.EnumSet

class Assembler : TileEntityBase(), traits.Environment, traits.PowerAcceptor, traits.Inventory, SidedEnvironment, traits.StateAware, traits.Tickable, DeviceInfo {
    @JvmField
    val node: Connector = ApiNetwork.newNode(this, Visibility.Network)
        .withComponent("assembler")
        .withConnector(Settings.get.bufferConverter)
        .create()

    override fun getNode() = node

    @JvmField
    var output: StackOption = EmptyStack

    @JvmField
    var totalRequiredEnergy: Double = 0.0

    @JvmField
    var requiredEnergy: Double = 0.0

    private val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Generic,
            DeviceAttribute.Description to "Assembler",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Factorizer R1D1"
        )
    }

    override fun getDeviceInfo(): java.util.Map<String, String> = deviceInfo as java.util.Map<String, String>

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = side != EnumFacing.UP

    override fun sidedNode(side: EnumFacing) = if (side != EnumFacing.UP) node else null

    @SideOnly(Side.CLIENT)
    override fun hasConnector(side: EnumFacing): Boolean = canConnect(side)

    override fun connector(side: EnumFacing): Connector? = if (side != EnumFacing.UP) node else null

    override fun energyThroughput(): Double = Settings.get.assemblerRate

    override fun getCurrentState(): EnumSet<StateAware.State> {
        return when {
            isAssembling -> EnumSet.of(StateAware.State.IsWorking)
            canAssemble -> EnumSet.of(StateAware.State.CanWork)
            else -> EnumSet.noneOf(StateAware.State::class.java)
        }
    }

    // ----------------------------------------------------------------------- //

    val canAssemble: Boolean
        get() {
            val template = AssemblerTemplates.select(getStackInSlot(0))
            return if (template != null) !isAssembling && output.isEmpty && template.validate(this)._1() else false
        }

    val isAssembling: Boolean
        get() = requiredEnergy > 0

    val progress: Double
        get() = (1 - requiredEnergy / totalRequiredEnergy) * 100

    val timeRemaining: Int
        get() = (requiredEnergy / Settings.get.assemblerTickAmount / 20).toInt()

    // ----------------------------------------------------------------------- //

    @JvmOverloads
    @Synchronized
    fun start(finishImmediately: Boolean = false): Boolean {
        val template = AssemblerTemplates.select(getStackInSlot(0))
        if (template != null && !isAssembling && output.isEmpty && template.validate(this)._1()) {
            for (slot in 0 until sizeInventory) {
                val stack = getStackInSlot(slot)
                if (!stack.isEmpty && !isItemValidForSlot(slot, stack)) return false
            }
            val result = template.assemble(this)
            val stack = result._1()
            val energy = result._2()
            output = StackOption(stack)
            totalRequiredEnergy = if (finishImmediately) 0.0 else maxOf(1.0, energy)
            requiredEnergy = totalRequiredEnergy
            ServerPacketSender.sendRobotAssembling(this, true)

            for (slot in 0 until sizeInventory) updateItems(slot, ItemStack.EMPTY)
            markDirty()

            return true
        }
        return false
    }

    // ----------------------------------------------------------------------- //

    @Callback(doc = """function(): string, number or boolean -- The current state of the assembler, `busy' or `idle', followed by the progress or template validity, respectively.""")
    fun status(context: Context, args: Arguments): Array<Any?> {
        return if (isAssembling) {
            result("busy", progress)
        } else {
            val template = AssemblerTemplates.select(getStackInSlot(0))
            if (template != null && template.validate(this)._1()) {
                result("idle", true)
            } else {
                result("idle", false)
            }
        }
    }

    @Callback(doc = """function():boolean -- Start assembling, if possible. Returns whether assembly was started or not.""")
    fun start(context: Context, args: Arguments): Array<Any?> = result(start())

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        super.updateEntity()
        if (!output.isEmpty && world.totalWorldTime % Settings.get.tickFrequency == 0L) {
            val want = maxOf(1.0, minOf(requiredEnergy, Settings.get.assemblerTickAmount * Settings.get.tickFrequency))
            val have = want + (if (Settings.get.ignorePower) 0.0 else node.changeBuffer(-want))
            requiredEnergy -= have
            if (requiredEnergy <= 0) {
                setInventorySlotContents(0, output.get())
                output = EmptyStack
                requiredEnergy = 0.0
            }
            ServerPacketSender.sendRobotAssembling(this, have > 0.5 && !output.isEmpty)
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val OutputTag = Settings.namespace + "output"
        private val OutputTagCompat = Settings.namespace + "robot"
        private val TotalTag = Settings.namespace + "total"
        private val RemainingTag = Settings.namespace + "remaining"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        if (nbt.hasKey(OutputTag)) {
            output = StackOption(ItemStack(nbt.getCompoundTag(OutputTag)))
        } else if (nbt.hasKey(OutputTagCompat)) {
            output = StackOption(ItemStack(nbt.getCompoundTag(OutputTagCompat)))
        }
        totalRequiredEnergy = nbt.getDouble(TotalTag)
        requiredEnergy = nbt.getDouble(RemainingTag)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        nbt.setNewCompoundTag(OutputTag) { output.get().writeToNBT(it) }
        nbt.setDouble(TotalTag, totalRequiredEnergy)
        nbt.setDouble(RemainingTag, requiredEnergy)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        requiredEnergy = nbt.getDouble(RemainingTag)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setDouble(RemainingTag, requiredEnergy)
    }

    // ----------------------------------------------------------------------- //

    override fun getSizeInventory(): Int = 22

    override fun getInventoryStackLimit(): Int = 1

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        return if (slot == 0) {
            !isAssembling && AssemblerTemplates.select(stack) != null
        } else {
            val template = AssemblerTemplates.select(getStackInSlot(0))
            if (template != null) {
                val tplSlot = when {
                    slot in 1..3 -> template.containerSlots()[slot - 1]
                    slot in 4..12 -> template.upgradeSlots()[slot - 4]
                    slot in 13..20 -> template.componentSlots()[slot - 13]
                    else -> AssemblerTemplates.NoSlot
                }
                tplSlot.validate(this, slot, stack)
            } else {
                false
            }
        }
    }
}
