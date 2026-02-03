package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.api.Driver
import li.cil.oc.api.Network
import li.cil.oc.api.component.RackBusConnectable
import li.cil.oc.api.component.RackMountable
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Rack
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.*
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.GuiType
import li.cil.oc.common.Slot
import li.cil.oc.common.Sound
import li.cil.oc.common.inventory.ComponentInventory
import li.cil.oc.common.inventory.Inventory
import li.cil.oc.common.inventory.ItemStackInventory
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedNBT.toNbt
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.Result
import li.cil.oc.util.ensureTagCompound
import li.cil.oc.util.result
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand

class DiskDriveMountable(
    val rack: Rack,
    val slot: Int
) : ManagedEnvironmentKt(), Inventory, ComponentInventory, RackMountable, Analyzable, DeviceInfo {
    // Stored for filling data packet when queried.
    var lastAccess = 0L

    val filesystemNode: Node?
        get() = components[0]?.node()

    // ----------------------------------------------------------------------- //
    // DeviceInfo

    private val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Disk,
        DeviceAttribute.Description to "Floppy disk drive",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "RackDrive 100 Rev. 2"
    )

    override fun getDeviceInfo() = deviceInfo

    // ----------------------------------------------------------------------- //
    // Environment

    override val node: Component = Network.newNode(this, Visibility.Network)!!
        .withComponent("disk_drive")
        .create() as Component

    @Callback(doc = "function():boolean -- Checks whether some medium is currently in the drive.")
    fun isEmpty(context: Context, args: Arguments): Result {
        return result(filesystemNode == null)
    }

    @Callback(doc = "function([velocity:number]):boolean -- Eject the currently present medium from the drive.")
    fun eject(context: Context, args: Arguments): Result {
        val velocity = args.optDouble(0, 0.0).coerceIn(0.0, 1.0)
        val ejected = decrStackSize(0, 1)
        if (!ejected.isEmpty) {
            val entity = InventoryUtils.spawnStackInWorld(BlockPosition(rack), ejected, rack.facing())
            if (entity != null) {
                val vx = rack.facing().xOffset * velocity
                val vy = rack.facing().yOffset * velocity
                val vz = rack.facing().zOffset * velocity
                entity.addVelocity(vx, vy, vz)
            }
            return result(true)
        } else {
            return result(false)
        }
    }

    @Callback(doc = "function(): string -- Return the internal floppy disk address")
    fun media(context: Context, args: Arguments): Result {
        return if (filesystemNode == null) {
            result(Unit, "drive is empty")
        } else {
            result(filesystemNode!!.address())
        }
    }

    // ----------------------------------------------------------------------- //
    // Analyzable

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node>? {
        return filesystemNode?.let { arrayOf(it) }
    }

    // ----------------------------------------------------------------------- //
    // ItemStackInventory

    override val host: EnvironmentHost get() = rack

    // ----------------------------------------------------------------------- //
    // IInventory

    private val inventory: Array<ItemStack> by lazy {
        Array(sizeInventory) { ItemStack.EMPTY }
    }

    override val items: Array<ItemStack>
        get() = inventory

    // Initialize the list automatically if we have a container.
    init {
        if (!container.isEmpty)
            reinitialize()
    }

    // Load items from tag.
    fun reinitialize() {
        for (i in items.indices)
            updateItems(i, ItemStack.EMPTY)
        load(container.ensureTagCompound)
    }

    // Write items back to tag.
    override fun markDirty() {
        save(container.ensureTagCompound)
    }

    override val componentInventoryDelegate = ComponentInventory.State()

    override fun getSizeInventory(): Int = 1

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        if (slot != 0) return false
        val driver = Driver.driverFor(stack) ?: return false
        return driver.slot(stack) == Slot.Floppy
    }

    override fun isUsableByPlayer(player: EntityPlayer): Boolean = rack.isUsableByPlayer(player)

    // ----------------------------------------------------------------------- //
    // ComponentInventory

    private val container: ItemStack get() = rack.getStackInSlot(slot)

    override fun onItemAdded(slot: Int, stack: ItemStack) {
        super<ComponentInventory>.onItemAdded(slot, stack)
        components[slot]?.node()?.let { node ->
            if (node is Component) {
                node.setVisibility(Visibility.Network)
            }
        }
        if (!rack.world.isRemote) {
            rack.markChanged(this.slot)
            Sound.playDiskInsert(rack)
        }
    }

    override fun onItemRemoved(slot: Int, stack: ItemStack) {
        super<ComponentInventory>.onItemRemoved(slot, stack)
        if (!rack.world.isRemote) {
            rack.markChanged(this.slot)
            Sound.playDiskEject(rack)
        }
    }

    // ----------------------------------------------------------------------- //
    // ManagedEnvironment

    override fun canUpdate(): Boolean = false

    // ----------------------------------------------------------------------- //
    // Persistable

    override fun load(nbt: NBTTagCompound) {
        super<ManagedEnvironmentKt>.load(nbt)
        super<ComponentInventory>.load(nbt)
        connectComponents()
    }

    override fun save(nbt: NBTTagCompound) {
        super<ManagedEnvironmentKt>.save(nbt)
        super<ComponentInventory>.save(nbt)
    }

    // ----------------------------------------------------------------------- //
    // RackMountable

    override fun getData(): NBTTagCompound {
        val nbt = NBTTagCompound()
        nbt.setLong("lastAccess", lastAccess)
        nbt.setTag("disk", toNbt(getStackInSlot(0)))
        return nbt
    }

    override fun getConnectableCount(): Int = 0

    override fun getConnectableAt(index: Int): RackBusConnectable? = null

    override fun onActivate(
        player: EntityPlayer,
        hand: EnumHand,
        heldItem: ItemStack,
        hitX: Float,
        hitY: Float
    ): Boolean {
        return if (player.isSneaking) {
            val isDiskInDrive = !getStackInSlot(0).isEmpty
            val isHoldingDisk = isItemValidForSlot(0, heldItem)
            if (isDiskInDrive) {
                if (!rack.world.isRemote) {
                    InventoryUtils.dropSlot(BlockPosition(rack), this, 0, 1, rack.facing())
                }
            }
            if (isHoldingDisk) {
                // Insert the disk.
                setInventorySlotContents(0, player.inventory.decrStackSize(player.inventory.currentItem, 1))
            }
            isDiskInDrive || isHoldingDisk
        } else {
            val position = BlockPosition(rack)
            player.openGui(
                OpenComputers.INSTANCE, GuiType.DiskDriveMountableInRack.id, rack.world,
                position.x, GuiType.embedSlot(position.y, slot), position.z
            )
            true
        }
    }

    // ----------------------------------------------------------------------- //
    // StateAware

    override fun getCurrentState(): java.util.EnumSet<li.cil.oc.api.util.StateAware.State> =
        java.util.EnumSet.noneOf(li.cil.oc.api.util.StateAware.State::class.java)
}
