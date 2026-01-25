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
import li.cil.oc.common.tileentity.behaviors.Behavior
import li.cil.oc.common.tileentity.behaviors.BehaviorLifecycle
import li.cil.oc.common.tileentity.behaviors.BehaviorUpdate
import li.cil.oc.common.tileentity.behaviors.NbtSeriailzable
import li.cil.oc.common.tileentity.traits.isServer
import li.cil.oc.common.tileentity.traits.world
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import net.minecraftforge.fml.common.Optional
import java.util.EnumSet

interface AppliedEnergistics2 : Common, IGridHost {
    private fun useAppliedEnergistics2Power(): Boolean = isServer && Mods.AppliedEnergistics2.isModAvailable

    val ae2Delegate: Delegate

    @Optional.InterfaceList(
        Optional.Interface(iface = "li.cil.oc.common.tileentity.behaviors.NbtSeriailzable", modid = Mods.IDs.AppliedEnergistics2),
        Optional.Interface(iface = "li.cil.oc.common.tileentity.behaviors.BehaviorUpdate", modid = Mods.IDs.AppliedEnergistics2),
        Optional.Interface(iface = "li.cil.oc.common.tileentity.behaviors.BehaviorLifecycle", modid = Mods.IDs.AppliedEnergistics2),
    )
    class Delegate(private val tile: AppliedEnergistics2): Behavior, NbtSeriailzable, BehaviorUpdate, BehaviorLifecycle {
        private var node: IGridNode? = null
        private var gridNodeStateUpdateRequested: Boolean = false

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        private fun requestGridNodeStateUpdate() {
            if (!gridNodeStateUpdateRequested) {
                EventHandler.scheduleAE2Add(tile)
                gridNodeStateUpdateRequested = true
            }
        }

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        private fun updateGridNodeState() {
            if (tile.asTileEntity().isInvalid)
                return
            val gridNode = getGridNode(AEPartLocation.INTERNAL)
            if (gridNode != null) {
                gridNode.updateState()
                gridNodeStateUpdateRequested = false
            }
        }

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        private fun updateEnergy() {
            tile.tryAllSides(Power::fromAE, Power::toAE) { demand, _ ->
                val grid = getGridNode(AEPartLocation.INTERNAL)?.grid ?: return@tryAllSides 0.0
                val cache = grid.getCache<IEnergyGrid>(IEnergyGrid::class.java) ?: return@tryAllSides 0.0
                return@tryAllSides cache.extractAEPower(demand, Actionable.MODULATE, PowerMultiplier.CONFIG)
            }
        }

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        override fun update() {
            if (tile.useAppliedEnergistics2Power() && Settings.get.isTickMultiple(tile.world))
                updateEnergy()
        }

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        override fun initialize() {
            if (tile.useAppliedEnergistics2Power())
                requestGridNodeStateUpdate()
        }

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        override fun dispose() {
            if (tile.useAppliedEnergistics2Power())
                securityBreak()
        }

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        override fun readFromNBTForServer(nbt: NBTTagCompound) {
            if (tile.useAppliedEnergistics2Power())
                loadNode(nbt)
        }

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        private fun loadNode(nbt: NBTTagCompound): Unit {
            getGridNode(AEPartLocation.INTERNAL)?.loadFromNBT(Settings.namespace + "ae2power", nbt)
        }

        private fun setWorld(worldIn: World?) {
            if (tile.world == worldIn)
                return
            if (worldIn != null && tile.isServer && tile.useAppliedEnergistics2Power())
                requestGridNodeStateUpdate()
        }

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        override fun writeToNBTForServer(nbt: NBTTagCompound) {
            if (tile.useAppliedEnergistics2Power())
                saveNode(nbt)
        }

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        private fun saveNode(nbt: NBTTagCompound): Unit {
            getGridNode(AEPartLocation.INTERNAL)?.saveToNBT(Settings.namespace + "ae2power", nbt)
        }

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        internal fun getGridNode(side: AEPartLocation): IGridNode? {
            if (node != null) return node
            if (tile.isServer) {
                node = AEApi.instance().grid().createGridNode(AppliedEnergistics2GridBlock(this))
                return node
            }
            return null
        }

        @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
        fun securityBreak() {
            getGridNode(AEPartLocation.INTERNAL)?.destroy()
        }
    }

    // ----------------------------------------------------------------------- //

    @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
    override fun getGridNode(side: AEPartLocation): IGridNode? = ae2Delegate.getGridNode(side)

    @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
    override fun getCableConnectionType(side: AEPartLocation): AECableType = AECableType.SMART

    @Optional.Method(modid = Mods.IDs.AppliedEnergistics2)
    override fun securityBreak() = ae2Delegate.securityBreak()
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
