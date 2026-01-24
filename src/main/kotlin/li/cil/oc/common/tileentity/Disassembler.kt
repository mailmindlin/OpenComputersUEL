package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.util.StateAware
import li.cil.oc.common.template.DisassemblerTemplates
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedNBT._
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.ItemUtils
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.Constants as NBTConstants
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.EnumSet
import li.cil.oc.common.tileentity.traits.Environment as TraitEnvironment
import li.cil.oc.common.tileentity.traits.PowerAcceptor as TraitPowerAcceptor
import li.cil.oc.common.tileentity.traits.Inventory as TraitInventory
import li.cil.oc.common.tileentity.traits.StateAware as TraitStateAware
import li.cil.oc.common.tileentity.traits.PlayerInputAware as TraitPlayerInputAware
import li.cil.oc.common.tileentity.traits.Tickable as TraitTickable

class Disassembler : TileEntityBase(), TraitEnvironment, TraitPowerAcceptor, TraitInventory, TraitStateAware, TraitPlayerInputAware, TraitTickable, DeviceInfo {
    @JvmField
    val node: Connector = ApiNetwork.newNode(this, Visibility.None)
        .withConnector(Settings.get.bufferConverter)
        .create()

    override fun getNode(): Node = node

    @JvmField
    var isActive = false

    @JvmField
    val queue: MutableList<ItemStack> = mutableListOf()

    @JvmField
    var totalRequiredEnergy = 0.0

    override fun getInventoryStackLimit(): Int = 1

    @JvmField
    var buffer = 0.0

    @JvmField
    var disassembleNextInstantly = false

    val progress: Double
        get() = if (queue.isEmpty()) 0.0 else (1 - (queue.size * Settings.get.disassemblerItemCost - buffer) / totalRequiredEnergy) * 100

    private fun setActive(value: Boolean) {
        if (value != isActive) {
            isActive = value
            ServerPacketSender.sendDisassemblerActive(this, isActive)
            world.notifyNeighborsOfStateChange(pos, blockType, true)
        }
    }

    private val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Generic,
            DeviceAttribute.Description to "Disassembler",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Break.3R-100"
        )
    }

    override fun getDeviceInfo(): java.util.Map<String, String> = deviceInfo as java.util.Map<String, String>

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun hasConnector(side: EnumFacing): Boolean = side != EnumFacing.UP

    override fun connector(side: EnumFacing): Connector? = if (side != EnumFacing.UP) node else null

    override fun energyThroughput(): Double = Settings.get.disassemblerRate

    override fun getCurrentState(): EnumSet<StateAware.State> {
        return when {
            isActive -> EnumSet.of(StateAware.State.IsWorking)
            queue.isNotEmpty() -> EnumSet.of(StateAware.State.CanWork)
            else -> EnumSet.noneOf(StateAware.State::class.java)
        }
    }

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        super.updateEntity()
        if (isServer && Settings.get.isTickMultiple(world)) {
            if (queue.isEmpty()) {
                val instant = disassembleNextInstantly // Is reset via decrStackSize
                disassemble(decrStackSize(0, 1), instant)
                setActive(queue.isNotEmpty())
            } else {
                if (buffer < Settings.get.disassemblerItemCost) {
                    val want = Settings.get.disassemblerTickAmount
                    val success = node.tryChangeBuffer(-want)
                    setActive(success) // If energy is insufficient indicate it visually.
                    if (success) {
                        buffer += want
                    }
                }
                while (buffer >= Settings.get.disassemblerItemCost && queue.isNotEmpty()) {
                    buffer -= Settings.get.disassemblerItemCost
                    val stack = queue.removeAt(0)
                    if (disassembleNextInstantly || world.rand.nextDouble() >= Settings.get.disassemblerBreakChance) {
                        drop(stack)
                    }
                }
            }
            disassembleNextInstantly = queue.isNotEmpty() // If we have nothing left to do, stop being creative.
        }
    }

    @JvmOverloads
    fun disassemble(stack: ItemStack, instant: Boolean = false) {
        // Validate the item, never trust Minecraft / other Mods on anything!
        if (isItemValidForSlot(0, stack)) {
            val ingredients = ItemUtils.getIngredients(stack)
            val template = DisassemblerTemplates.select(stack)
            if (template != null) {
                val result = template.disassemble(stack, ingredients)
                val stacks = result._1()
                val drops = result._2()
                stacks?.let { queue.addAll(it) }
                drops?.forEach { it?.forEach { item -> drop(item) } }
            } else {
                queue.addAll(ingredients)
            }
            totalRequiredEnergy = queue.size * Settings.get.disassemblerItemCost
            if (instant) {
                buffer = totalRequiredEnergy
            }
        } else {
            drop(stack)
        }
    }

    private fun drop(stack: ItemStack) {
        if (!stack.isEmpty) {
            for (side in EnumFacing.values()) {
                if (stack.count > 0) {
                    InventoryUtils.insertIntoInventoryAt(stack, BlockPosition(this).offset(side), side.opposite)
                }
            }
            if (stack.count > 0) {
                spawnStackInWorld(stack, EnumFacing.UP)
            }
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val QueueTag = Settings.namespace + "queue"
        private val BufferTag = Settings.namespace + "buffer"
        private val TotalTag = Settings.namespace + "total"
        private val IsActiveTag = Settings.namespace + "isActive"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        queue.clear()
        val tagList = nbt.getTagList(QueueTag, NBTConstants.NBT.TAG_COMPOUND)
        for (i in 0 until tagList.tagCount()) {
            queue.add(ItemStack(tagList.getCompoundTagAt(i)))
        }
        buffer = nbt.getDouble(BufferTag)
        totalRequiredEnergy = nbt.getDouble(TotalTag)
        isActive = queue.isNotEmpty()
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        val tagList = NBTTagList()
        for (stack in queue) {
            val tag = NBTTagCompound()
            stack.writeToNBT(tag)
            tagList.appendTag(tag)
        }
        nbt.setTag(QueueTag, tagList)
        nbt.setDouble(BufferTag, buffer)
        nbt.setDouble(TotalTag, totalRequiredEnergy)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        isActive = nbt.getBoolean(IsActiveTag)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setBoolean(IsActiveTag, isActive)
    }

    // ----------------------------------------------------------------------- //

    override fun getSizeInventory(): Int = 1

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean =
        allowDisassembling(stack) &&
            (((Settings.get.disassembleAllTheThings || ApiItems.get(stack) != null) && ItemUtils.getIngredients(stack).isNotEmpty()) ||
                DisassemblerTemplates.select(stack) != null)

    private fun allowDisassembling(stack: ItemStack): Boolean =
        !stack.isEmpty && (!stack.hasTagCompound() || !stack.tagCompound!!.getBoolean(Settings.namespace + "undisassemblable"))

    override fun setInventorySlotContents(slot: Int, stack: ItemStack) {
        super.setInventorySlotContents(slot, stack)
        if (!world.isRemote) {
            disassembleNextInstantly = false
        }
    }

    override fun onSetInventorySlotContents(player: EntityPlayer, slot: Int, stack: ItemStack) {
        if (!world.isRemote) {
            disassembleNextInstantly = !stack.isEmpty && slot == 0 && player.capabilities.isCreativeMode
        }
    }
}
