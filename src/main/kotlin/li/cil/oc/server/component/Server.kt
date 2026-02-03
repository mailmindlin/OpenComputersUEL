package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.api.component.RackBusConnectable
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Rack
import li.cil.oc.api.internal.Server
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.machine.MachineHost
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.util.StateAware
import li.cil.oc.common.GuiType
import li.cil.oc.common.InventorySlots
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.inventory.ComponentInventory
import li.cil.oc.common.inventory.ServerInventory
import li.cil.oc.common.item.Delegator
import li.cil.oc.server.network.Connector
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider
import java.util.*
import li.cil.oc.api.Machine as MachineFactory
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.common.item.Server as ItemServer

class Server(val rack: Rack, val slot: Int) : ServerInventory(), Environment, MachineHost, ComponentInventory, Analyzable, Server, ICapabilityProvider, DeviceInfo {
    val machine: Machine = MachineFactory.create(this)!!
    override fun machine(): Machine = machine
    override fun rack(): Rack = rack
    override fun slot(): Int = slot

    val node: Node? = if (!rack.world.isRemote) machine.node() else null

    var wasRunning = false
    var hadErrored = false
    var lastFileSystemAccess = 0L
    var lastNetworkActivity = 0L

    private val deviceInfo_ by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.System,
            DeviceAttribute.Description to "Server",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Blader",
            DeviceAttribute.Capacity to sizeInventory.toString()
        )
    }

    override fun getDeviceInfo() = deviceInfo_

    // ----------------------------------------------------------------------- //
    // Environment

    override fun node(): Node? = node

    override fun onConnect(node: Node) {
        if (node == this.node) {
            connectComponents()
        }
    }

    override fun onDisconnect(node: Node) {
        if (node == this.node) {
            disconnectComponents()
        }
    }

    override fun onMessage(message: Message) {}

    private val MachineTag = "machine"

    override fun load(nbt: NBTTagCompound) {
        super<ComponentInventory>.load(nbt)
        if (!rack.world.isRemote) {
            machine.load(nbt.getCompoundTag(MachineTag))
        }
    }

    override fun save(nbt: NBTTagCompound) {
        super<ComponentInventory>.save(nbt)
        if (!rack.world.isRemote) {
            nbt.setNewCompoundTag(MachineTag) { machine.save(it) }
        }
    }

    // ----------------------------------------------------------------------- //
    // MachineHost

    override fun internalComponents(): Iterable<ItemStack> {
        return (0 until getSizeInventory())
            .filter { slot -> !getStackInSlot(slot).isEmpty && isComponentSlot(slot, getStackInSlot(slot)) }
            .map { slot -> getStackInSlot(slot) }
    }

    override fun componentSlot(address: String): Int {
        return components.indexOfFirst { env ->
            env?.node()?.address() == address
        }
    }

    override fun onMachineConnect(node: Node) = onConnect(node)

    override fun onMachineDisconnect(node: Node) = onDisconnect(node)

    // ----------------------------------------------------------------------- //
    // EnvironmentHost

    override fun xPosition(): Double = rack.xPosition()
    override fun yPosition(): Double = rack.yPosition()
    override fun zPosition(): Double = rack.zPosition()
    override fun world(): World = rack.world()

    override fun markChanged() = rack.markChanged()

    // ----------------------------------------------------------------------- //
    // ServerInventory

    override val tier: Int
        get() {
            val subItem = Delegator.subItem(container)
            return if (subItem is ItemServer) subItem.tier else 0
        }

    override fun isUsableByPlayer(player: EntityPlayer): Boolean = rack.isUsableByPlayer(player)

    // ----------------------------------------------------------------------- //
    // ItemStackInventory

    override val host: Rack get() = rack

    // ----------------------------------------------------------------------- //
    // ComponentInventory

    override val componentInventoryDelegate = ComponentInventory.State()

    override val container: ItemStack get() = rack.getStackInSlot(slot)

    override fun connectItemNode(node: Node?) {
        if (node != null) {
            ApiNetwork.joinNewNetwork(machine.node())
            machine.node()!!.connect(node)
        }
    }

    override fun onItemRemoved(slot: Int, stack: ItemStack) {
//        super<ServerInventory>.onItemRemoved(slot, stack) // is no-op
        super<ComponentInventory>.onItemRemoved(slot, stack)
        if (!rack.world.isRemote) {
            val slotType = InventorySlots.server[tier][slot].slot
            if (slotType == Slot.CPU) {
                machine.stop()
            }
        }
    }

    override fun getInventoryStackLimit(): Int = super<ComponentInventory>.getInventoryStackLimit()

    // ----------------------------------------------------------------------- //
    // RackMountable

    override fun getData(): NBTTagCompound {
        val nbt = NBTTagCompound()
        nbt.setBoolean("isRunning", wasRunning)
        nbt.setBoolean("hasErrored", hadErrored)
        nbt.setLong("lastFileSystemAccess", lastFileSystemAccess)
        nbt.setLong("lastNetworkActivity", lastNetworkActivity)
        return nbt
    }

    override fun getConnectableCount(): Int = components.count { it is RackBusConnectable }

    override fun getConnectableAt(index: Int): RackBusConnectable? {
        return components.filterIsInstance<RackBusConnectable>().getOrNull(index)
    }

    override fun onActivate(player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, hitX: Float, hitY: Float): Boolean {
        if (!player.entityWorld.isRemote) {
            if (player.isSneaking) {
                if (!machine.isRunning && isUsableByPlayer(player)) {
                    wasRunning = false
                    hadErrored = false
                    machine.start()
                }
            } else {
                val position = BlockPosition(rack)
                player.openGui(OpenComputers.INSTANCE, GuiType.ServerInRack.id, world(), position.x, GuiType.embedSlot(position.y, slot), position.z)
            }
        }
        return true
    }

    // ----------------------------------------------------------------------- //
    // ManagedEnvironment

    override fun canUpdate(): Boolean = true

    override fun update() {
        if (!rack.world.isRemote) {
            machine.update()

            val isRunning = machine.isRunning
            val hasErrored = machine.lastError() != null
            if (isRunning != wasRunning || hasErrored != hadErrored) {
                rack.markChanged(slot)
            }
            wasRunning = isRunning
            hadErrored = hasErrored
            if (tier == Tier.Four) {
                (node as? Connector)?.changeBuffer(Double.POSITIVE_INFINITY)
            }
        }

        updateComponents()
    }

    // ----------------------------------------------------------------------- //
    // StateAware

    override fun getCurrentState(): EnumSet<StateAware.State> {
        return if (machine.isRunning) EnumSet.of(StateAware.State.IsWorking)
        else EnumSet.noneOf(StateAware.State::class.java)
    }

    // ----------------------------------------------------------------------- //
    // Analyzable

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node>? {
        val n = machine.node()
        return if (n != null) arrayOf(n) else null
    }

    // ----------------------------------------------------------------------- //
    // ICapabilityProvider

    override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
        return components.any { component ->
            (component as? ICapabilityProvider)?.hasCapability(capability, facing?.let { host.toLocal(it) }) == true
        }
    }

    override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
        for (component in components) {
            val provider = component as? ICapabilityProvider ?: continue
            val localFacing = facing?.let { host.toLocal(it) }
            if (provider.hasCapability(capability, localFacing)) {
                return provider.getCapability(capability, localFacing)
            }
        }
        return null
    }
}
