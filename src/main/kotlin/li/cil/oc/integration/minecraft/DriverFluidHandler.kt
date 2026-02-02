package li.cil.oc.integration.minecraft

import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fluids.capability.CapabilityFluidHandler
import net.minecraftforge.fluids.capability.IFluidHandler

class DriverFluidHandler : DriverBlock {
    override fun worksWith(world: World, pos: BlockPos, side: EnumFacing): Boolean {
        val tileEntity = world.getTileEntity(pos) ?: return false
        return tileEntity.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side) &&
                tileEntity.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side) != null
    }

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing)
        = world.getTileEntity(pos)?.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side)?.let(::Environment)

    class Environment(tileEntity: IFluidHandler) :
        ManagedTileEntityEnvironment<IFluidHandler>(tileEntity, "fluid_handler") {
        @Callback(doc = "function():table -- Get some information about the tank accessible from the specified side.")
        fun getTankInfo(context: Context?, args: Arguments?): Array<out Any> {
            return tileEntity.tankProperties
        }
    }
}
