package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.Slot
import li.cil.oc.common.Sound
import li.cil.oc.common.tileentity.traits.ComponentInventory
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.InventoryUtils
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class DiskDrive : TileEntityBase(), traits.Environment, ComponentInventory, traits.Rotatable, Analyzable, DeviceInfo {
    // Used on client side to check whether to render disk activity indicators.
    @JvmField
    var lastAccess = 0L

    val filesystemNode: Node?
        get() = components.getOrNull(0)?.node

    private val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Disk,
            DeviceAttribute.Description to "Floppy disk drive",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Spinner 520p1"
        )
    }

    override fun getDeviceInfo(): java.util.Map<String, String> = deviceInfo as java.util.Map<String, String>

    // ----------------------------------------------------------------------- //
    // Environment

    @JvmField
    val node: Component = api.Network.newNode(this, Visibility.Network)
        .withComponent("disk_drive")
        .create()

    override fun getNode(): Node = node

    @Callback(doc = "function():boolean -- Checks whether some medium is currently in the drive.")
    fun isEmpty(context: Context, args: Arguments): Array<Any?> {
        return result(filesystemNode == null)
    }

    @Callback(doc = "function([velocity:number]):boolean -- Eject the currently present medium from the drive.")
    fun eject(context: Context, args: Arguments): Array<Any?> {
        val velocity = maxOf(0.0, minOf(1.0, args.optDouble(0, 0.0)))
        val ejected = decrStackSize(0, 1)
        return if (!ejected.isEmpty) {
            val entity = InventoryUtils.spawnStackInWorld(position, ejected, facing)
            if (entity != null) {
                val vx = facing.xOffset * velocity
                val vy = facing.yOffset * velocity
                val vz = facing.zOffset * velocity
                entity.addVelocity(vx, vy, vz)
            }
            result(true)
        } else {
            result(false)
        }
    }

    @Callback(doc = "function(): string -- Return the internal floppy disk address")
    fun media(context: Context, args: Arguments): Array<Any?> {
        val fsNode = filesystemNode
        return if (fsNode == null) {
            result(Unit, "drive is empty")
        } else {
            result(fsNode.address)
        }
    }

    // ----------------------------------------------------------------------- //
    // Analyzable

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node>? {
        val fsNode = filesystemNode
        return if (fsNode != null) arrayOf(fsNode) else null
    }

    // ----------------------------------------------------------------------- //
    // IInventory

    override fun getSizeInventory(): Int = 1

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        if (slot == 0) {
            val driver = Driver.driverFor(stack, javaClass)
            return driver != null && driver.slot(stack) == Slot.Floppy
        }
        return false
    }

    // ----------------------------------------------------------------------- //
    // ComponentInventory

    override fun onItemAdded(slot: Int, stack: ItemStack) {
        super.onItemAdded(slot, stack)
        components.getOrNull(slot)?.let { environment ->
            (environment.node as? Component)?.setVisibility(Visibility.Network)
        }
        if (isServer) {
            ServerPacketSender.sendFloppyChange(this, stack)
            Sound.playDiskInsert(this)
        }
    }

    override fun onItemRemoved(slot: Int, stack: ItemStack) {
        super.onItemRemoved(slot, stack)
        if (isServer) {
            ServerPacketSender.sendFloppyChange(this)
            Sound.playDiskEject(this)
        }
    }

    // ----------------------------------------------------------------------- //
    // TileEntity

    companion object {
        private val DiskTag = Settings.namespace + "disk"
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        if (nbt.hasKey(DiskTag)) {
            setInventorySlotContents(0, ItemStack(nbt.getCompoundTag(DiskTag)))
        }
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        if (!items[0].isEmpty) {
            nbt.setNewCompoundTag(DiskTag) { items[0].writeToNBT(it) }
        }
    }
}
