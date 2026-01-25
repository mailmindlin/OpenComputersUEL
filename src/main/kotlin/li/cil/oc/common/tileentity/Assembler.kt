package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
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
import li.cil.oc.common.tileentity.traits.Inventory
import li.cil.oc.common.tileentity.traits.power.AppliedEnergistics2
import li.cil.oc.common.tileentity.traits.power.IndustrialCraft2Experimental
import li.cil.oc.server.component.DeviceInfoKt
import li.cil.oc.server.component.result
import li.cil.oc.util.notEmpty
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.text.ITextComponent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.*
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.common.tileentity.traits.Inventory as TraitInventory
import li.cil.oc.common.tileentity.traits.PowerAcceptor as TraitPowerAcceptor
import li.cil.oc.common.tileentity.traits.StateAware as TraitStateAware
import li.cil.oc.common.tileentity.traits.Tickable as TraitTickable
import li.cil.oc.server.PacketSender as ServerPacketSender

class Assembler : TileEntityBase.TEEnvironmentBase(), TraitPowerAcceptor, TraitInventory, SidedEnvironment, TraitStateAware, TraitTickable, DeviceInfoKt {
    @JvmField
    val node: Connector = ApiNetwork.newNode(this, Visibility.Network)
        .withComponent("assembler")
        .withConnector(Settings.get.bufferConverter)
        .create()
    override fun node() = node
    override val ic2Delegate: IndustrialCraft2Experimental.Delegate = IndustrialCraft2Experimental.Delegate(this)
    override val ae2Delegate: AppliedEnergistics2.Delegate = AppliedEnergistics2.Delegate(this)
    override val inventoryDelegate: Inventory.Delegate = Inventory.Delegate(this)

    init {
        behaviors.register(ic2Delegate)
        behaviors.register(ae2Delegate)
        behaviors.register(inventoryDelegate)
    }

    @JvmField
    var output: ItemStack? = null

    @JvmField
    var totalRequiredEnergy: Double = 0.0

    @JvmField
    var requiredEnergy: Double = 0.0

    override val deviceInfo: Map<String, String> get() = Companion.deviceInfo

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = side != EnumFacing.UP

    override fun sidedNode(side: EnumFacing) = if (side != EnumFacing.UP) node else null

    override fun connector(side: EnumFacing?): Connector? = if (side != EnumFacing.UP) node else null

    override val energyThroughput: Double
        get() = Settings.get.assemblerRate

    override fun getCurrentState(): EnumSet<StateAware.State> {
        return when {
            isAssembling -> EnumSet.of(StateAware.State.IsWorking)
            canAssemble -> EnumSet.of(StateAware.State.CanWork)
            else -> EnumSet.noneOf(StateAware.State::class.java)
        }
    }


    override fun getDisplayName(): ITextComponent
            = super<Inventory>.getDisplayName()

    // ----------------------------------------------------------------------- //

    val canAssemble: Boolean
        get() {
            val template = AssemblerTemplates.select(getStackInSlot(0)) ?: return false
            return !isAssembling && output == null && template.validate(this).first
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
        if (template != null && !isAssembling && output == null && template.validate(this).first) {
            for (slot in 0 until sizeInventory) {
                val stack = getStackInSlot(slot)
                if (!stack.isEmpty && !isItemValidForSlot(slot, stack)) return false
            }
            val (stack, energy) = template.assemble(this)
            output = stack.notEmpty()
            totalRequiredEnergy = if (finishImmediately) 0.0 else energy.coerceAtLeast(1.0)
            requiredEnergy = totalRequiredEnergy
            ServerPacketSender.sendRobotAssembling(this, true)

            for (slot in 0 until sizeInventory) updateItems(slot, ItemStack.EMPTY)
            markDirty()

            return true
        }
        return false
    }

    // ----------------------------------------------------------------------- //

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function(): string, number or boolean -- The current state of the assembler, `busy' or `idle', followed by the progress or template validity, respectively.""")
    fun status(context: Context, args: Arguments): Array<Any?> {
        return if (isAssembling) {
            result("busy", progress)
        } else {
            val template = AssemblerTemplates.select(getStackInSlot(0))
            val valid = template?.validate(this)?.first ?: false
            result("idle", valid)
        }
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = """function():boolean -- Start assembling, if possible. Returns whether assembly was started or not.""")
    fun start(context: Context, args: Arguments): Array<Any?> = result(start())

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        super.updateEntity()
        val output = output
        if (output != null && Settings.get.isTickMultiple(world)) {
            val want = maxOf(1.0, minOf(requiredEnergy, Settings.get.assemblerTickAmount * Settings.get.tickFrequency))
            val have = want + (if (Settings.get.ignorePower) 0.0 else node.changeBuffer(-want))
            requiredEnergy -= have
            if (requiredEnergy <= 0) {
                setInventorySlotContents(0, output)
                this.output = null
                requiredEnergy = 0.0
            }
            ServerPacketSender.sendRobotAssembling(this, have > 0.5 && !output.isEmpty)
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        val deviceInfo = mapOf(
            DeviceAttribute.Class to DeviceClass.Generic,
            DeviceAttribute.Description to "Assembler",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Factorizer R1D1"
        )
        private val OutputTag = Settings.namespace + "output"
        private val OutputTagCompat = Settings.namespace + "robot"
        private val TotalTag = Settings.namespace + "total"
        private val RemainingTag = Settings.namespace + "remaining"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        output = if (nbt.hasKey(OutputTag)) {
            ItemStack(nbt.getCompoundTag(OutputTag))
        } else if (nbt.hasKey(OutputTagCompat)) {
            ItemStack(nbt.getCompoundTag(OutputTagCompat))
        } else {
            null
        }

        totalRequiredEnergy = nbt.getDouble(TotalTag)
        requiredEnergy = nbt.getDouble(RemainingTag)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        nbt.setNewCompoundTag(OutputTag) { output!!.writeToNBT(it) }
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
                    slot in 1..3 -> template.containerSlots[slot - 1]
                    slot in 4..12 -> template.upgradeSlots[slot - 4]
                    slot in 13..20 -> template.componentSlots[slot - 13]
                    else -> AssemblerTemplates.NoSlot
                }
                tplSlot.validate(this, slot, stack)
            } else {
                false
            }
        }
    }
}
