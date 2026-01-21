package li.cil.oc.common.block

import li.cil.oc.common.tileentity.Transposer as TETransposer
import net.minecraft.block.state.IBlockState
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World

class Transposer : SimpleBlock() {
    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = false

    // ----------------------------------------------------------------------- //

    override fun createNewTileEntity(world: World, meta: Int) = TETransposer()
}
