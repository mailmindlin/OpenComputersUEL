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
import li.cil.oc.api.network.Node
import li.cil.oc.SidedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.EventHandler
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import li.cil.oc.server.PacketSender as ServerPacketSender
import net.minecraft.init.SoundEvents
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.SoundCategory
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class NetSplitter : TileEntityBase(), traits.Environment(), traits.OpenSides, traits.RedstoneAware, SidedEnvironment, DeviceInfo {
    private val deviceInfo: java.util.Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Network,
            DeviceAttribute.Description to "Ethernet controller",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "NetSplits",
            DeviceAttribute.Version to "1.0",
            DeviceAttribute.Width to "6"
        ) as java.util.Map<String, String>
    }

    override fun getDeviceInfo(): java.util.Map<String, String> = deviceInfo

    init {
        _isOutputEnabled = true
    }

    @JvmField
    val node: Node = ApiNetwork.newNode(this, Visibility.Network)
        .withComponent("net_splitter", Visibility.Network)
        .create()

    override fun getNode(): Node = node

    @JvmField
    var isInverted = false

    override fun isSideOpen(side: EnumFacing): Boolean = if (isInverted) !super.isSideOpen(side) else super.isSideOpen(side)

    override fun setSideOpen(side: EnumFacing, value: Boolean) {
        val previous = isSideOpen(side)
        super.setSideOpen(side, value)
        if (previous != isSideOpen(side)) {
            if (isServer) {
                node.remove()
                ApiNetwork.joinOrCreateNetwork(this)
                ServerPacketSender.sendNetSplitterState(this)
                world.playSound(null, x + 0.5, y + 0.5, z + 0.5, SoundEvents.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 0.5f, world.rand.nextFloat() * 0.25f + 0.7f)
                world.notifyNeighborsOfStateChange(pos, blockType, false)
            } else {
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun sidedNode(side: EnumFacing): Node? = if (isSideOpen(side)) node else null

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = isSideOpen(side)

    // ----------------------------------------------------------------------- //

    override fun initialize() {
        super.initialize()
        EventHandler.scheduleServer(this)
    }

    // ----------------------------------------------------------------------- //

    override fun onRedstoneInputChanged(args: RedstoneChangedEventArgs) {
        super.onRedstoneInputChanged(args)
        val oldIsInverted = isInverted
        isInverted = args.newValue > 0
        if (isInverted != oldIsInverted) {
            if (isServer) {
                node.remove()
                ApiNetwork.joinOrCreateNetwork(this)
                ServerPacketSender.sendNetSplitterState(this)
                world.playSound(null, x + 0.5, y + 0.5, z + 0.5, SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 0.5f, world.rand.nextFloat() * 0.25f + 0.7f)
            } else {
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
            }
        }
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val IsInvertedTag = Settings.namespace + "isInverted"
        private val OpenSidesTag = Settings.namespace + "openSides"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        isInverted = nbt.getBoolean(IsInvertedTag)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        nbt.setBoolean(IsInvertedTag, isInverted)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        isInverted = nbt.getBoolean(IsInvertedTag)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setBoolean(IsInvertedTag, isInverted)
    }

    // component api
    fun currentStatus(): MutableMap<Int, Boolean> {
        val openStatus = mutableMapOf<Int, Boolean>()
        for (side in EnumFacing.VALUES) {
            openStatus[side.ordinal] = isSideOpen(side)
        }
        return openStatus
    }

    fun setSide(side: EnumFacing, state: Boolean): Boolean {
        val previous = isSideOpen(side) // isSideOpen uses inverter
        setSideOpen(side, if (isInverted) !state else state) // but setSideOpen does not
        return previous != state
    }

    @Callback(doc = "function(settings:table):table -- set open state (true/false) of all sides in an array; index by direction. Returns previous states")
    fun setSides(context: Context, args: Arguments): Array<Any?> {
        val settings = args.checkTable(0)
        val previous = currentStatus()
        for (side in EnumFacing.VALUES) {
            val ordinal = side.ordinal
            val value = if (settings.containsKey(ordinal)) {
                when (val v = settings[ordinal]) {
                    is Boolean -> v
                    else -> false
                }
            } else false
            setSide(side, value)
        }
        return result(previous)
    }

    @Callback(direct = true, doc = "function():table -- Returns current open/close state of all sides in an array, indexed by direction.")
    fun getSides(context: Context, args: Arguments): Array<Any?> = result(currentStatus())

    fun setSideHelper(args: Arguments, value: Boolean): Array<Any?> {
        val sideIndex = args.checkInteger(0)
        if (sideIndex < 0 || sideIndex > 5)
            return result(Unit, "invalid direction")
        val side = EnumFacing.byIndex(sideIndex)
        return result(setSide(side, value))
    }

    @Callback(doc = "function(side: number):boolean -- Open the side, returns true if it changed to open.")
    fun open(context: Context, args: Arguments): Array<Any?> = setSideHelper(args, value = true)

    @Callback(doc = "function(side: number):boolean -- Close the side, returns true if it changed to close.")
    fun close(context: Context, args: Arguments): Array<Any?> = setSideHelper(args, value = false)
}
