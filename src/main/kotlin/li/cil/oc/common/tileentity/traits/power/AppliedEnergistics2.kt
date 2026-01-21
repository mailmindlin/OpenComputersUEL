package li.cil.oc.common.tileentity.traits.power

import appeng.api.AEApi
import appeng.api.config.Actionable
import appeng.api.config.PowerMultiplier
import appeng.api.networking.GridFlags
import appeng.api.networking.GridNotification
import appeng.api.networking.IGrid
import appeng.api.networking.IGridBlock
import appeng.api.networking.IGridHost
import appeng.api.networking.IGridNode
import appeng.api.networking.energy.IEnergyGrid
import appeng.api.util.AECableType
import appeng.api.util.AEColor
import appeng.api.util.AEPartLocation
import appeng.api.util.DimensionalCoord
import li.cil.oc.Settings
import li.cil.oc.common.EventHandler
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import net.minecraftforge.fml.common.Optional
import java.util.EnumSet

interface AppliedEnergistics2 : Common, IGridHost {
    val world: World?

    fun getPos(): net.minecraft.util.math.BlockPos

    fun isInvalid(): Boolean

    fun readFromNBTForServer(nbt: NBTTagCompound)

    fun writeToNBTForServer(nbt: NBTTagCompound)

    fun updateEntity()

    // Mixin-like property for the grid node - implementations need to provide storage
    var ae2GridNode: IGridNode?
    var ae2GridNodeStateUpdateRequested: Boolean

    private fun useAppliedEnergistics2Power(): Boolean = isServer && Mods.AppliedEnergistics2.isModAvailable

    fun requestGridNodeStateUpdate() {
        if (!ae2GridNodeStateUpdateRequested) {
            EventHandler.scheduleAE2Add(this as net.minecraft.tileentity.TileEntity)
            ae2GridNodeStateUpdateRequested = true
        }
    }

    fun updateGridNodeState() {
        if (!isInvalid()) {
            val gridNode = getGridNode(AEPartLocation.INTERNAL)
            if (gridNode != null) {
                gridNode.updateState()
                ae2GridNodeStateUpdateRequested = false
            }
        }
    }

    fun updateAE2Entity() {
        if (useAppliedEnergistics2Power() && world != null && world!!.totalWorldTime % Settings.get.tickFrequency == 0L) {
            updateAE2Energy()
        }
    }

    @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
    private fun updateAE2Energy() {
        tryAllSides({ demand, _ ->
            val grid = getGridNode(AEPartLocation.INTERNAL)?.grid
            if (grid != null) {
                val cache = grid.getCache<IEnergyGrid>(IEnergyGrid::class.java)
                if (cache != null) {
                    cache.extractAEPower(demand, Actionable.MODULATE, PowerMultiplier.CONFIG)
                } else 0.0
            } else 0.0
        }, Power::fromAE, Power::toAE)
    }

    fun validateAE2() {
        if (useAppliedEnergistics2Power()) requestGridNodeStateUpdate()
    }

    fun invalidateAE2() {
        if (useAppliedEnergistics2Power()) securityBreak()
    }

    fun onChunkUnloadAE2() {
        if (useAppliedEnergistics2Power()) securityBreak()
    }

    // ----------------------------------------------------------------------- //

    fun readAE2FromNBTForServer(nbt: NBTTagCompound) {
        if (useAppliedEnergistics2Power()) loadAE2Node(nbt)
    }

    @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
    private fun loadAE2Node(nbt: NBTTagCompound) {
        getGridNode(AEPartLocation.INTERNAL)?.loadFromNBT(Settings.namespace + "ae2power", nbt)
    }

    fun setWorldAE2(worldIn: World?) {
        if (worldIn != null && isServer && useAppliedEnergistics2Power()) {
            requestGridNodeStateUpdate()
        }
    }

    fun writeAE2ToNBTForServer(nbt: NBTTagCompound) {
        if (useAppliedEnergistics2Power()) saveAE2Node(nbt)
    }

    @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
    private fun saveAE2Node(nbt: NBTTagCompound) {
        getGridNode(AEPartLocation.INTERNAL)?.saveToNBT(Settings.namespace + "ae2power", nbt)
    }

    // ----------------------------------------------------------------------- //

    @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
    override fun getGridNode(side: AEPartLocation): IGridNode? {
        if (ae2GridNode != null) return ae2GridNode
        if (isServer) {
            ae2GridNode = AEApi.instance().grid().createGridNode(AppliedEnergistics2GridBlock(this))
            return ae2GridNode
        }
        return null
    }

    @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
    override fun getCableConnectionType(side: AEPartLocation): AECableType = AECableType.SMART

    @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
    override fun securityBreak() {
        getGridNode(AEPartLocation.INTERNAL)?.destroy()
    }
}

class AppliedEnergistics2GridBlock(private val tileEntity: AppliedEnergistics2) : IGridBlock {
    override fun getIdlePowerUsage(): Double = 0.0

    override fun getFlags(): EnumSet<GridFlags> = EnumSet.noneOf(GridFlags::class.java)

    override fun isWorldAccessible(): Boolean = true

    override fun getLocation(): DimensionalCoord = DimensionalCoord(tileEntity as net.minecraft.tileentity.TileEntity)

    override fun getGridColor(): AEColor = AEColor.TRANSPARENT

    override fun onGridNotification(notification: GridNotification) {}

    override fun setNetworkStatus(grid: IGrid, channelsInUse: Int) {}

    override fun getConnectableSides(): EnumSet<EnumFacing> {
        val connectableSides = EnumFacing.values().filter { tileEntity.canConnectPower(it) }
        return if (connectableSides.isEmpty()) {
            EnumSet.noneOf(EnumFacing::class.java)
        } else {
            EnumSet.copyOf(connectableSides)
        }
    }

    override fun getMachine(): IGridHost = tileEntity

    override fun gridChanged() {}

    override fun getMachineRepresentation(): ItemStack = ItemStack.EMPTY
}
