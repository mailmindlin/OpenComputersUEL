package li.cil.oc.common.block.traits

import li.cil.oc.api.util.StateAware as ApiStateAware
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

interface StateAware {
    fun stateAwareHasComparatorInputOverride(state: IBlockState): Boolean = true

    fun stateAwareGetComparatorInputOverride(state: IBlockState, world: World, pos: BlockPos): Int {
        val tileEntity = world.getTileEntity(pos) as? li.cil.oc.common.tileentity.traits.StateAware ?: return 0
        val currentState = tileEntity.currentState
        return when {
            ApiStateAware.State.IsWorking in tileEntity.currentState -> 15
            ApiStateAware.State.CanWork in tileEntity.currentState -> 10
            else -> 0
        }
    }
}
