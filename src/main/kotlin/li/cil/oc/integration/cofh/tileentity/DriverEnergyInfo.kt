package li.cil.oc.integration.cofh.tileentity

import cofh.api.tileentity.IEnergyInfo
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class DriverEnergyInfo : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = IEnergyInfo::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing)
        = (world.getTileEntity(pos) as? IEnergyInfo)?.let(::Environment)

    class Environment(tileEntity: IEnergyInfo) : ManagedTileEntityEnvironment<IEnergyInfo>(tileEntity, "energy_info") {
        @Callback(doc = "function():number --  Returns the amount of stored energy.")
        fun getEnergy(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.infoEnergyStored)
        }

        @Callback(doc = "function():number --  Returns the energy per tick.")
        fun getEnergyPerTick(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.infoEnergyPerTick)
        }

        @Callback(doc = "function():number --  Returns the maximum energy per tick.")
        fun getMaxEnergyPerTick(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.infoMaxEnergyPerTick)
        }
    }
}
