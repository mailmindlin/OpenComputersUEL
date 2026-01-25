package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.FileSystem as ApiFileSystem
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.fs.Label
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.Slot
import li.cil.oc.common.item.data.DriveData
import li.cil.oc.common.item.data.NodeData
import li.cil.oc.common.tileentity.traits.Inventory
import li.cil.oc.common.tileentity.traits.Rotatable
import li.cil.oc.common.tileentity.traits.isServer
import li.cil.oc.server.component.FileSystem
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.UUID
import li.cil.oc.common.tileentity.traits.Environment as TraitEnvironment
import li.cil.oc.common.tileentity.traits.Inventory as TraitInventory
import li.cil.oc.common.tileentity.traits.Rotatable as TraitRotatable

class Raid : TileEntityBase(), TraitEnvironment, TraitInventory, TraitRotatable, Analyzable {
    override val rotatableDelegate: Rotatable.RotatableDelegate = register(Rotatable::RotatableDelegate)
    override val inventoryDelegate: TraitInventory.Delegate = register(TraitInventory::Delegate)

    @JvmField
    val node: Node = ApiNetwork.newNode(this, Visibility.None).create()

    override fun node(): Node = node

    @JvmField
    var filesystem: FileSystem? = null

    @JvmField
    val label = RaidLabel()

    // Used on client side to check whether to render disk activity indicators.
    @JvmField
    var lastAccess = 0L

    // For client side rendering.
    @JvmField
    val presence = Array(sizeInventory) { false }

    // ----------------------------------------------------------------------- //

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node?> =
        arrayOf(filesystem?.node())

    // ----------------------------------------------------------------------- //

    override fun getSizeInventory(): Int = 3

    override fun getInventoryStackLimit(): Int = 1

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean =
        Driver.driverFor(stack, javaClass)?.let { driver -> driver.slot(stack) == Slot.HDD } ?: false

    override fun onItemAdded(slot: Int, stack: ItemStack) {
        super.onItemAdded(slot, stack)
        if (isServer) synchronized(this) {
            ServerPacketSender.sendRaidChange(this)
            tryCreateRaid(UUID.randomUUID().toString())
        }
    }

    override fun markDirty() {
        super.markDirty()
        // Makes the implementation of the comparator output easier.
        items.map { !it.isEmpty }.toTypedArray().copyInto(presence)
    }

    override fun onItemRemoved(slot: Int, stack: ItemStack) {
        super.onItemRemoved(slot, stack)
        if (isServer) synchronized(this) {
            ServerPacketSender.sendRaidChange(this)
            filesystem?.let { fs ->
                fs.fileSystem.close()
                fs.fileSystem.list("/").forEach { fs.fileSystem.delete(it) }
                fs.save(NBTTagCompound()) // Flush buffered fs.
                fs.node().remove()
                filesystem = null
            }
        }
    }

    fun tryCreateRaid(id: String) {
        if (items.count { !it.isEmpty } == items.size && (filesystem == null || filesystem!!.node() == null || filesystem!!.node().address() != id)) {
            filesystem?.let { fs -> if (fs.node() != null) fs.node().remove() }
            items.forEach { fsStack ->
                val drive = DriveData(fsStack)
                drive.lockInfo = ""
                drive.isUnmanaged = false
                drive.save(fsStack)
            }
            val fs = ApiFileSystem.asManagedEnvironment(
                ApiFileSystem.fromSaveDirectory(id, wipeDisksAndComputeSpace(), Settings.get.bufferChanges),
                label, this, Settings.resourceDomain + ":hdd_access", 6
            ) as FileSystem
            val nbtToSetAddress = NBTTagCompound()
            nbtToSetAddress.setString(NodeData.AddressTag, id)
            fs.node().load(nbtToSetAddress)
            fs.node().setVisibility(Visibility.Network)
            // Ensure we're in a network before connecting the raid fs.
            ApiNetwork.joinNewNetwork(node)
            node.connect(fs.node())
            filesystem = fs
        }
    }

    private fun wipeDisksAndComputeSpace(): Long = items.fold(0L) { acc, hdd ->
        if (!hdd.isEmpty) {
            acc + (Driver.driverFor(hdd)?.let { driver ->
                val env = driver.createEnvironment(hdd, this)
                if (env is FileSystem) {
                    val nbt = driver.dataTag(hdd)
                    env.load(nbt)
                    env.fileSystem.close()
                    env.fileSystem.list("/").forEach { env.fileSystem.delete(it) }
                    env.save(nbt)
                    env.fileSystem.spaceTotal()
                } else 0L
            } ?: 0L)
        } else acc
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val FileSystemTag = Settings.namespace + "fs"
        private val PresenceTag = Settings.namespace + "presence"
        private val LabelTag = Settings.namespace + "label"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        if (nbt.hasKey(FileSystemTag)) {
            val tag = nbt.getCompoundTag(FileSystemTag)
            tryCreateRaid(tag.getCompoundTag(NodeData.NodeTag).getString(NodeData.AddressTag))
            filesystem?.load(tag)
        }
        label.load(nbt)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        filesystem?.let { fs -> nbt.setNewCompoundTag(FileSystemTag) { fs.save(it) } }
        label.save(nbt)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        nbt.getByteArray(PresenceTag)
            .map { it != 0.toByte() }
            .toTypedArray()
            .copyInto(presence)
        label.setLabel(nbt.getString(LabelTag))
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setByteArray(PresenceTag, items.map { if (!it.isEmpty) 1.toByte() else 0.toByte() }.toByteArray())
        if (label.getLabel() != null)
            nbt.setString(LabelTag, label.getLabel())
    }

    // ----------------------------------------------------------------------- //

    inner class RaidLabel : Label {
        var label: String = "raid"

        override fun getLabel(): String = label

        override fun setLabel(value: String?) {
            label = value?.take(16) ?: ""
        }

        override fun load(nbt: NBTTagCompound) {
            if (nbt.hasKey(Settings.namespace + "label")) {
                label = nbt.getString(Settings.namespace + "label")
            }
        }

        override fun save(nbt: NBTTagCompound) {
            nbt.setString(Settings.namespace + "label", label)
        }
    }
}
