package li.cil.oc.integration.forestry

import forestry.api.genetics.AlleleManager
import forestry.core.tiles.TileAnalyzer
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.Result
import li.cil.oc.util.result
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class DriverAnalyzer : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = TileAnalyzer::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing) =
        Environment(world.getTileEntity(pos) as TileAnalyzer)

    class Environment(tileEntity: TileAnalyzer) :
        ManagedTileEntityEnvironment<TileAnalyzer>(tileEntity, "forestry_analyzer"), NamedBlock {

        override fun preferredName() = "forestry_analyzer"

        override fun priority() = 0

        @Callback(doc = "function():boolean -- Get whether the analyzer can work.")
        fun isWorking(context: Context, args: Arguments): Result =
            result(tileEntity.hasWork())

        @Callback(doc = "function():double -- Get the progress of the current operation.")
        fun getProgress(context: Context, args: Arguments): Result =
            result(1.0 - tileEntity.getProgressScaled(100) / 100.0)

        @Callback(doc = "function():table -- Get info on the currently present bee.")
        fun getIndividualOnDisplay(context: Context, args: Arguments): Result =
            result(AlleleManager.alleleRegistry.getIndividual(tileEntity.individualOnDisplay))
    }
}
