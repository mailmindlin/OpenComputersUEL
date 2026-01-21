package li.cil.oc.common.block

import li.cil.oc.common.tileentity.RedstoneAware as TERedstoneAware
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World

abstract class RedstoneAware : SimpleBlock() {
    override fun canProvidePower(state: IBlockState): Boolean = true

    override fun canConnectRedstone(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing?): Boolean {
        val tileEntity = world.getTileEntity(pos)
        return tileEntity is TERedstoneAware && tileEntity.isOutputEnabled
    }

    override fun getStrongPower(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Int =
        getWeakPower(state, world, pos, side)

    override fun getWeakPower(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing?): Int {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TERedstoneAware && side != null) {
            maxOf(tileEntity.getOutput(side.opposite), 0)
        } else super.getWeakPower(state, world, pos, side)
    }

    // ----------------------------------------------------------------------- //

    override fun neighborChanged(state: IBlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos) {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TERedstoneAware) {
            tileEntity.checkRedstoneInputChanged()
        }
    }
}
