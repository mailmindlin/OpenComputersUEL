package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.network.Analyzable
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity.traits.Environment as TraitEnvironment
import li.cil.oc.common.tileentity.traits.ComponentInventory as TraitComponentInventory
import li.cil.oc.common.tileentity.traits.Tickable as TraitTickable
import li.cil.oc.common.tileentity.traits.OpenSides as TraitOpenSides
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.internal.Adapter as InternalAdapter
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.SoundEvents
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.EnumFacing
import net.minecraft.util.SoundCategory
import net.minecraftforge.common.util.Constants as NBTConstants

class Adapter : TileEntityBase(), TraitEnvironment, TraitComponentInventory, TraitTickable, TraitOpenSides, Analyzable, InternalAdapter, DeviceInfo {
    @JvmField
    val node: Node = ApiNetwork.newNode(this, Visibility.Network).create()

    override fun getNode(): Node = node

    private val blocks: Array<Pair<ManagedEnvironment, DriverBlock>?> = arrayOfNulls(6)

    private val updatingBlocks: MutableList<ManagedEnvironment> = mutableListOf()

    private val blocksData: Array<BlockData?> = arrayOfNulls(6)

    private val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Bus,
            DeviceAttribute.Description to "Adapter",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Multiplug Ext.1"
        )
    }

    override fun getDeviceInfo(): Map<String, String> = deviceInfo

    // ----------------------------------------------------------------------- //

    override val defaultState: Boolean = true

    override fun setSideOpen(side: EnumFacing, value: Boolean) {
        super.setSideOpen(side, value)
        if (isServer) {
            ServerPacketSender.sendAdapterState(this)
            world.playSound(null, x + 0.5, y + 0.5, z + 0.5, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.5f, world.rand.nextFloat() * 0.25f + 0.7f)
            world.notifyNeighborsOfStateChange(pos, blockType, false)
            neighborChanged(side)
        } else {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
        }
    }

    // ----------------------------------------------------------------------- //

    override fun onAnalyze(player: EntityPlayer, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Array<Node> {
        val blockNodes = blocks.mapNotNull { it?.first?.node() }
        val componentNodes = components.mapNotNull { it?.node() }
        return (blockNodes + componentNodes).toTypedArray()
    }

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        super.updateEntity()
        if (isServer && updatingBlocks.isNotEmpty()) {
            for (block in updatingBlocks) {
                block.update()
            }
        }
    }

    fun neighborChanged(d: EnumFacing) {
        if (node != null && node.network() != null) {
            val blockPos = pos.offset(d)
            when (world.getTileEntity(blockPos)) {
                is TraitEnvironment -> {
                    // Don't provide adaption for our stuffs. This is mostly to avoid
                    // cables and other non-functional stuff popping up in the adapter
                    // due to having a power interface. Might revisit this at some point,
                    // but the only 'downside' is that it can't be used to manipulate
                    // inventories, which I actually consider a plus :P
                }
                else -> {
                    val newDriver = api.Driver.driverFor(world, blockPos, d)
                    if (newDriver != null && isSideOpen(d)) {
                        val existing = blocks[d.ordinal]
                        if (existing != null) {
                            val (oldEnvironment, driver) = existing
                            if (newDriver != driver) {
                                // This is... odd. Maybe moved by some other mod? First, clean up.
                                blocks[d.ordinal] = null
                                updatingBlocks.remove(oldEnvironment)
                                blocksData[d.ordinal] = null
                                node.disconnect(oldEnvironment.node())

                                // Then rebuild - if we have something.
                                val environment = newDriver.createEnvironment(world, blockPos, d)
                                if (environment != null) {
                                    blocks[d.ordinal] = environment to newDriver
                                    if (environment.canUpdate()) {
                                        updatingBlocks.add(environment)
                                    }
                                    blocksData[d.ordinal] = BlockData(environment.javaClass.name, NBTTagCompound())
                                    node.connect(environment.node())
                                }
                            } // else: the more things change, the more they stay the same.
                        } else {
                            if (!isSideOpen(d)) {
                                return
                            }
                            // A challenger appears. Maybe.
                            val environment = newDriver.createEnvironment(world, blockPos, d)
                            if (environment != null) {
                                blocks[d.ordinal] = environment to newDriver
                                if (environment.canUpdate()) {
                                    updatingBlocks.add(environment)
                                }
                                val data = blocksData[d.ordinal]
                                if (data != null && data.name == environment.javaClass.name) {
                                    environment.load(data.data)
                                }
                                blocksData[d.ordinal] = BlockData(environment.javaClass.name, NBTTagCompound())
                                node.connect(environment.node())
                            }
                        }
                    } else {
                        val existing = blocks[d.ordinal]
                        if (existing != null) {
                            val (environment, _) = existing
                            // We had something there, but it's gone now...
                            node.disconnect(environment.node())
                            blocksData[d.ordinal]?.let { environment.save(it.data) }
                            environment.node()?.remove()
                            blocks[d.ordinal] = null
                            updatingBlocks.remove(environment)
                        } // else: Nothing before, nothing now.
                    }
                }
            }
        }
    }

    fun neighborChanged() {
        if (node != null && node.network() != null) {
            for (d in EnumFacing.values()) {
                neighborChanged(d)
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun onConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node) {
            neighborChanged()
        }
    }

    override fun onDisconnect(node: Node) {
        super.onDisconnect(node)
        if (node == this.node) {
            updatingBlocks.clear()
        }
    }

    // ----------------------------------------------------------------------- //

    override fun getSizeInventory(): Int = 1

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        if (slot == 0) {
            val driver = Driver.driverFor(stack, javaClass)
            return driver != null && driver.slot(stack) == Slot.Upgrade
        }
        return false
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val BlocksTag = Settings.namespace + "adapter.blocks"
        private const val BlockNameTag = "name"
        private const val BlockDataTag = "data"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)

        val blocksNbt = nbt.getTagList(BlocksTag, NBTConstants.NBT.TAG_COMPOUND)
        for (i in 0 until minOf(blocksNbt.tagCount(), blocksData.size)) {
            val blockNbt = blocksNbt.getCompoundTagAt(i)
            if (blockNbt.hasKey(BlockNameTag) && blockNbt.hasKey(BlockDataTag)) {
                blocksData[i] = BlockData(blockNbt.getString(BlockNameTag), blockNbt.getCompoundTag(BlockDataTag))
            }
        }
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)

        val blocksNbt = NBTTagList()
        for (i in blocks.indices) {
            val blockNbt = NBTTagCompound()
            blocksData[i]?.let { data ->
                blocks[i]?.let { (environment, _) -> environment.save(data.data) }
                blockNbt.setString(BlockNameTag, data.name)
                blockNbt.setTag(BlockDataTag, data.data)
            }
            blocksNbt.appendTag(blockNbt)
        }
        nbt.setTag(BlocksTag, blocksNbt)
    }

    // ----------------------------------------------------------------------- //

    private data class BlockData(val name: String, val data: NBTTagCompound)
}
