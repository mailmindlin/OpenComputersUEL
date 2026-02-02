package li.cil.oc.integration.minecraft

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fluids.IFluidTank

class DriverFluidTank : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = IFluidTank::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing)
        = (world.getTileEntity(pos) as? IFluidTank)?.let(::Environment)

    class Environment(tileEntity: IFluidTank) : ManagedTileEntityEnvironment<IFluidTank>(tileEntity, "fluid_tank") {
        @Callback(doc = "function():table -- Get some information about this tank.")
        fun getInfo(context: Context?, args: Arguments?): Array<out Any>
            = arrayOf(tileEntity.info)
    }
}