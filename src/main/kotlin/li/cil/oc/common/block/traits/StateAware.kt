package li.cil.oc.common.block.traits

import li.cil.oc.api
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

interface StateAware {
    fun stateAwareHasComparatorInputOverride(state: IBlockState): Boolean = true

    fun stateAwareGetComparatorInputOverride(state: IBlockState, world: World, pos: BlockPos): Int {
        val tileEntity = world.getTileEntity(pos)
        return when (tileEntity) {
            is StateAware -> {
                when {
                    tileEntity.currentState.contains(api.util.StateAware.State.IsWorking) -> 15
                    tileEntity.currentState.contains(api.util.StateAware.State.CanWork) -> 10
                    else -> 0
                }
            }
            else -> 0
        }
    }
}
