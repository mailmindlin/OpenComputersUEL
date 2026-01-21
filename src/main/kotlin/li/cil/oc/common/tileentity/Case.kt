package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Driver
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal
import li.cil.oc.api.network.Connector
import li.cil.oc.common
import li.cil.oc.common.InventorySlots
import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.block.property.PropertyRunning
import li.cil.oc.util.Color
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class Case @JvmOverloads constructor(
    @JvmField var tier: Int = 0
) : TileEntityBase(), traits.PowerAcceptor(), traits.Computer, traits.Colored, internal.Case, DeviceInfo {

    init {
        // If no tier was defined when constructing this case, then we don't yet know the inventory size
        // this is set back to true when the nbt data is loaded
        if (tier == 0) {
            isSizeInventoryReady = false
        }
        setColor(Color.rgbValues(Color.byTier(tier)))
    }

    // Used on client side to check whether to render disk activity/network indicators.
    @JvmField
    var lastFileSystemAccess = 0L

    @JvmField
    var lastNetworkActivity = 0L

    private val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.System,
            DeviceAttribute.Description to "Computer",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Blocker",
            DeviceAttribute.Capacity to sizeInventory.toString()
        )
    }

    override fun getDeviceInfo(): java.util.Map<String, String> = deviceInfo as java.util.Map<String, String>

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun hasConnector(side: EnumFacing): Boolean = side != facing

    override fun connector(side: EnumFacing): Connector? =
        if (side != facing && machine != null) machine.node as? Connector else null

    override fun energyThroughput(): Double = Settings.get.caseRate(tier)

    val isCreative: Boolean
        get() = tier == Tier.Four

    // ----------------------------------------------------------------------- //

    override fun componentSlot(address: String): Int =
        components.indexOfFirst { it?.node != null && it.node.address == address }

    // ----------------------------------------------------------------------- //

    override fun updateEntity() {
        if (isServer && isCreative && world.totalWorldTime % Settings.get.tickFrequency == 0L) {
            // Creative case, make it generate power.
            (node as Connector).changeBuffer(Double.POSITIVE_INFINITY)
        }
        super.updateEntity()
    }

    // ----------------------------------------------------------------------- //

    override fun onRunningChanged() {
        super.onRunningChanged()
        val block = blockType
        if (block is common.block.Case) {
            val state = world.getBlockState(pos)
            // race condition that the world no longer has this block at the position (e.g. it was broken)
            if (block == state.block) {
                world.setBlockState(pos, state.withProperty(PropertyRunning.Running, isRunning))
            }
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val TierTag = Settings.namespace + "tier"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        tier = maxOf(0, minOf(3, nbt.getByte(TierTag).toInt()))
        setColor(Color.rgbValues(Color.byTier(tier)))
        super.readFromNBTForServer(nbt)
        isSizeInventoryReady = true
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        nbt.setByte(TierTag, tier.toByte())
        super.writeToNBTForServer(nbt)
    }

    // ----------------------------------------------------------------------- //

    override fun onItemAdded(slot: Int, stack: ItemStack) {
        super.onItemAdded(slot, stack)
        if (isServer) {
            if (InventorySlots.computer(tier)[slot].slot == Slot.Floppy) {
                common.Sound.playDiskInsert(this)
            }
        }
    }

    override fun onItemRemoved(slot: Int, stack: ItemStack) {
        super.onItemRemoved(slot, stack)
        if (isServer) {
            val slotType = InventorySlots.computer(tier)[slot].slot
            if (slotType == Slot.Floppy) {
                common.Sound.playDiskEject(this)
            }
            if (slotType == Slot.CPU) {
                machine.stop()
            }
        }
    }

    override fun getSizeInventory(): Int =
        if (tier < 0 || tier >= InventorySlots.computer.size) 0 else InventorySlots.computer(tier).size

    override fun isUsableByPlayer(player: EntityPlayer): Boolean =
        super.isUsableByPlayer(player) && (!isCreative || player.capabilities.isCreativeMode)

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean {
        val driver = Driver.driverFor(stack, javaClass)
        return if (driver != null) {
            val provided = InventorySlots.computer(tier)[slot]
            driver.slot(stack) == provided.slot && driver.tier(stack) <= provided.tier
        } else {
            false
        }
    }
}
