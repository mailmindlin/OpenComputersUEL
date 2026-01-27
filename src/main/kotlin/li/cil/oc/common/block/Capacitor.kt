package li.cil.oc.common.block

import li.cil.oc.common.tileentity.Capacitor as TECapacitor
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.Random

open class Capacitor : SimpleBlock() {
    init {
        setTickRandomly(true)
    }

    // ----------------------------------------------------------------------- //

    override fun createNewTileEntity(world: World, metadata: Int) = TECapacitor()

    // ----------------------------------------------------------------------- //

    override fun hasComparatorInputOverride(state: IBlockState): Boolean = true

    override fun getComparatorInputOverride(state: IBlockState, world: World, pos: BlockPos): Int {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TECapacitor && !world.isRemote) {
            Math.round(15 * tileEntity.node!!.localBuffer() / tileEntity.node.localBufferSize()).toInt()
        } else 0
    }

    override fun updateTick(world: World, pos: BlockPos, state: IBlockState, rand: Random) {
        world.notifyNeighborsOfStateChange(pos, this, false)
    }

    override fun tickRate(world: World): Int = 1

    override fun neighborChanged(state: IBlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos) {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TECapacitor) {
            tileEntity.recomputeCapacity()
        }
    }
}
