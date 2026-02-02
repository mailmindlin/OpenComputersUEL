package li.cil.oc.integration.ic2

import ic2.core.block.TileEntityBlock
import ic2.core.block.comp.Energy
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class DriverEnergy : DriverBlock {
    override fun worksWith(world: World, pos: BlockPos, side: EnumFacing): Boolean {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TileEntityBlock) {
            return tileEntity.hasComponent(Energy::class.java)
        }
        return false
    }

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing)
        = (world.getTileEntity(pos) as? TileEntityBlock?)?.let(::Environment)

    class Environment(tileEntity: TileEntityBlock) :
        ManagedTileEntityEnvironment<TileEntityBlock>(tileEntity, "ic2_energy") {
        @Callback
        fun getCapacity(context: Context?, args: Arguments?): Array<Any> {
            val energy = tileEntity.getComponent(Energy::class.java)
            return arrayOf(energy.capacity)
        }

        @Callback
        fun getEnergy(context: Context?, args: Arguments?): Array<Any> {
            val energy = tileEntity.getComponent(Energy::class.java)
            return arrayOf(energy.energy)
        }

        @Callback
        fun getSinkTier(context: Context?, args: Arguments?): Array<Any> {
            val energy = tileEntity.getComponent(Energy::class.java)
            return arrayOf(energy.sinkTier)
        }

        @Callback
        fun getSourceTier(context: Context?, args: Arguments?): Array<Any> {
            val energy = tileEntity.getComponent(Energy::class.java)
            return arrayOf(energy.sourceTier)
        }
    }
}
