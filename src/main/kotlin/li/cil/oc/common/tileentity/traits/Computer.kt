package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.Machine as ApiMachine
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.util.StateAware
import li.cil.oc.api.network.Node
import li.cil.oc.client.Sound
import li.cil.oc.common.tileentity.RobotProxy
import li.cil.oc.common.tileentity.TileEntityBase
import li.cil.oc.common.tileentity.register
import li.cil.oc.integration.opencomputers.DriverRedstoneCard
import li.cil.oc.server.agent.Player
import li.cil.oc.util.setNewCompoundTag
import li.cil.oc.util.setNewTagList
import li.cil.oc.server.PacketSender as ServerPacketSender
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagString
import net.minecraft.util.EnumFacing
import net.minecraft.util.text.ITextComponent
import net.minecraftforge.common.util.Constants.NBT
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.EnumSet

/**
 * Base computer (Computer/Robot/Microcontroller) TileEntity
 */
abstract class Computer : TileEntityBase.TEEnvironmentBase(), ComponentInventory, Rotatable, BundledRedstoneAware, Analyzable, MachineHost, StateAware, Tickable {
    private val _machine: Machine? by lazy { if (isServer) ApiMachine.create(this) else null }
    open val machine: Machine? get() = _machine
    override fun machine(): Machine? = machine

    protected open val makeRedstoneDelegate: (Computer) -> BundledRedstoneAware.Delegate get() = BundledRedstoneAware::Delegate

    override val redstoneDelegate: BundledRedstoneAware.Delegate = register(this.makeRedstoneDelegate)
    override val componentInventoryDelegate: ComponentInventory.Delegate = register(ComponentInventory::Delegate)
    override val inventoryDelegate: Inventory.Delegate = register(Inventory::Delegate)
    // Don't implement rotatableDelegate because implementers might use TileRotatable

    override fun node(): Node? = if (isServer) machine?.node() else null

    private var _isRunning = false

    // For client side rendering of error LED indicator.
    var hasErrored = false

    private val _users = mutableSetOf<String>()

    protected open val runSound: String? get() = "computer_running"

    // ----------------------------------------------------------------------- //

    open fun canInteract(player: String): Boolean =
        if (isServer) machine?.canInteract(player) ?: false
        else !Settings.get.canComputersBeOwned || _users.isEmpty() || _users.contains(player)

    open val isRunning: Boolean get() = _isRunning

    open fun setRunning(value: Boolean) {
        if (value != _isRunning) {
            _isRunning = value
            if (value) {
                hasErrored = false
            }
            if (getWorld() != null) {
                getWorld().notifyBlockUpdate(pos, getWorld().getBlockState(pos), getWorld().getBlockState(pos), 3)
                if (getWorld().isRemote) {
                    val sound = runSound
                    if (sound != null) {
                        if (_isRunning) Sound.startLoop(this, sound, 0.5f, (50 + getWorld().rand.nextInt(50)).toLong())
                        else Sound.stopLoop(this)
                    }
                }
            }
        }
    }

    @SideOnly(Side.CLIENT)
    fun setUsers(list: Iterable<String>) {
        _users.clear()
        _users.addAll(list)
    }

    override fun getCurrentState(): EnumSet<StateAware.State> {
        return if (isRunning) EnumSet.of(StateAware.State.IsWorking)
        else EnumSet.noneOf(StateAware.State::class.java)
    }

    // ----------------------------------------------------------------------- //

    override fun getDisplayName(): ITextComponent
        = super<ComponentInventory>.getDisplayName()

    override fun internalComponents(): Iterable<ItemStack> {
        return (0 until getSizeInventory())
            .filter { slot -> !getStackInSlot(slot).isEmpty && isComponentSlot(slot, getStackInSlot(slot)) }
            .map { slot -> getStackInSlot(slot) }
    }

    override fun onMachineConnect(node: Node) = this.onConnect(node)

    override fun onMachineDisconnect(node: Node) = this.onDisconnect(node)

    open fun hasRedstoneCard(): Boolean = items.any { item ->
        !item.isEmpty && machine?.isRunning == true && DriverRedstoneCard.worksWith(item, javaClass)
    }

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        // If we're not yet in a network we might have just been loaded from disk,
        // meaning there may be other tile entities that also have not re-joined
        // the network. We skip the update this round to allow other tile entities
        // to join the network, too, avoiding issues of missing nodes (e.g. in the
        // GPU which would otherwise loose track of its screen).
        if (isServer && isConnected) {
            updateComputer()

            val running = machine?.isRunning ?: false
            val errored = machine?.lastError() != null
            if (_isRunning != running || hasErrored != errored) {
                _isRunning = running
                hasErrored = errored
                onRunningChanged()
            }

            updateComponents()
        }

        super<TEEnvironmentBase>.updateEntity()
    }

    protected open fun updateComputer() {
        machine?.update()
    }

    protected open fun onRunningChanged() {
        markDirty()
        ServerPacketSender.sendComputerState(this)
    }

    override fun dispose() {
        super<TEEnvironmentBase>.dispose()
        if (machine != null && this !is RobotProxy) {
            machine?.stop()
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val ComputerTag = Settings.namespace + "computer"
        private val HasErroredTag = Settings.namespace + "hasErrored"
        private val IsRunningTag = Settings.namespace + "isRunning"
        private val UsersTag = Settings.namespace + "users"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super<TEEnvironmentBase>.readFromNBTForServer(nbt)
        // God, this is so ugly... will need to rework the robot architecture.
        // This is required for loading auxiliary data (kernel state), because the
        // coordinates in the actual robot won't be set properly, otherwise.
        if (this is RobotProxy) {
            this.robot.setPos(pos)
        }
        machine?.load(nbt.getCompoundTag(ComputerTag))

        // Kickstart initialization to avoid values getting overwritten by
        // readFromNBTForClient if that packet is handled after a manual
        // initialization / state change packet.
        setRunning(machine?.isRunning ?: false)
        redstoneDelegate._isOutputEnabled = hasRedstoneCard()
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super<TEEnvironmentBase>.writeToNBTForServer(nbt)
        machine?.let { machine ->
            nbt.setNewCompoundTag(ComputerTag) { machine.save(it) }
        }
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        hasErrored = nbt.getBoolean(HasErroredTag)
        setRunning(nbt.getBoolean(IsRunningTag))
        _users.clear()
        val usersList = nbt.getTagList(UsersTag, NBT.TAG_STRING)
        for (i in 0 until usersList.tagCount()) {
            _users.add(usersList.getStringTagAt(i))
        }
        if (_isRunning) {
            runSound?.let { sound -> Sound.startLoop(this, sound, 0.5f, (1000 + getWorld().rand.nextInt(2000)).toLong()) }
        }
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setBoolean(HasErroredTag, machine != null && machine?.lastError() != null)
        nbt.setBoolean(IsRunningTag, isRunning)
        nbt.setNewTagList(UsersTag, machine?.users()?.map { user -> NBTTagString(user) } ?: emptyList())
    }

    // ----------------------------------------------------------------------- //

    override fun markDirty() {
        super.markDirty()
        if (isServer) {
            machine?.onHostChanged()
            this.outputEnabled = hasRedstoneCard()
        }
    }

    override fun isUsableByPlayer(player: EntityPlayer): Boolean =
        super.isUsableByPlayer(player) && when (player) {
            is Player -> canInteract(player.agent.ownerName())
            else -> canInteract(player.name)
        }

    override fun onRotationChanged() {
        super.onRotationChanged()
        checkRedstoneInputChanged()
    }

    override fun onRedstoneInputChanged(args: RedstoneChangedEventArgs) {
        super.onRedstoneInputChanged(args)
        val toLocalSide = toLocal(args.side!!)
        if (toLocalSide != null) {
            val toLocalArgs = RedstoneChangedEventArgs(toLocalSide, args.oldValue, args.newValue, args.color)
            machine?.node()?.sendToNeighbors("redstone.changed", toLocalArgs)
        }
    }

    // ----------------------------------------------------------------------- //

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node>? {
        val node = machine?.node()
        return if (node != null) arrayOf(node) else null
    }
}
