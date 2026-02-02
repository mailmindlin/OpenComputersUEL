package li.cil.oc.integration.greg

import gregtech.api.capability.IGhostSlotConfigurable
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverGhostCircuitCircuit : DriverBlock {
    override fun worksWith(world: World, pos: BlockPos, side: EnumFacing?): Boolean
        = (world.getTileEntity(pos) as? IGhostSlotConfigurable)?.hasGhostCircuitInventory() == true

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing?)
        = (world.getTileEntity(pos) as? IGhostSlotConfigurable)?.takeIf { it.hasGhostCircuitInventory() }?.let(::Environment)

    class Environment(tileEntity: IGhostSlotConfigurable) : ManagedTileEntityEnvironment<IGhostSlotConfigurable>(tileEntity, "greg_circuit") {
        @Callback(doc="function(config:int):boolean -- Set circuit configuration")
        fun setGhostCircuit(context: Context, args: Arguments): Array<Any> {
            val index = args.checkInteger(0)
            tileEntity.setGhostCircuitConfig(index)
            return arrayOf(true)
        }
    }
}
