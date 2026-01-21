package li.cil.oc.util

import net.minecraft.block.Block
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraftforge.fluids.IFluidBlock

/**
 * Wrapper object that provides extension-like methods on Block.
 * Use via: import li.cil.oc.util.ExtendedBlock.extendedBlock
 * Then call: block.extendedBlock().someMethod()
 */
object ExtendedBlock {
    @JvmStatic
    fun Block.extendedBlock(): ExtendedBlockWrapper = ExtendedBlockWrapper(this)
}

class ExtendedBlockWrapper(private val block: Block) {
    fun isAir(position: BlockPosition): Boolean =
        block.isAir(position.world!!.getBlockState(position.toBlockPos()), position.world!!, position.toBlockPos())

    fun isReplaceable(position: BlockPosition): Boolean =
        block.isReplaceable(position.world!!, position.toBlockPos())

    fun getBlockHardness(position: BlockPosition): Float =
        block.getBlockHardness(position.world!!.getBlockState(position.toBlockPos()), position.world!!, position.toBlockPos())

    fun getSelectedBoundingBoxFromPool(position: BlockPosition): AxisAlignedBB =
        block.getSelectedBoundingBox(position.world!!.getBlockState(position.toBlockPos()), position.world!!, position.toBlockPos())

    fun getCollisionBoundingBoxFromPool(position: BlockPosition): AxisAlignedBB? =
        block.getCollisionBoundingBox(position.world!!.getBlockState(position.toBlockPos()), position.world!!, position.toBlockPos())

    fun getComparatorInputOverride(position: BlockPosition, @Suppress("UNUSED_PARAMETER") side: EnumFacing): Int =
        block.getComparatorInputOverride(position.world!!.getBlockState(position.toBlockPos()), position.world!!, position.toBlockPos())
}

// Direct extension functions (for backwards compatibility)
fun Block.isAir(position: BlockPosition): Boolean =
    isAir(position.world!!.getBlockState(position.toBlockPos()), position.world, position.toBlockPos())

fun Block.isReplaceable(position: BlockPosition): Boolean =
    isReplaceable(position.world!!, position.toBlockPos())

fun Block.getBlockHardness(position: BlockPosition): Float =
    getBlockHardness(position.world!!.getBlockState(position.toBlockPos()), position.world!!, position.toBlockPos())

fun Block.getSelectedBoundingBoxFromPool(position: BlockPosition) =
    getSelectedBoundingBox(position.world!!.getBlockState(position.toBlockPos()), position.world!!, position.toBlockPos())

fun Block.getCollisionBoundingBoxFromPool(position: BlockPosition) =
    getCollisionBoundingBox(position.world!!.getBlockState(position.toBlockPos()), position.world!!, position.toBlockPos())

@Suppress("UNUSED_PARAMETER")
fun Block.getComparatorInputOverride(position: BlockPosition, side: EnumFacing): Int =
    getComparatorInputOverride(position.world!!.getBlockState(position.toBlockPos()), position.world!!, position.toBlockPos())

fun IFluidBlock.drain(position: BlockPosition, doDrain: Boolean) =
    drain(position.world!!, position.toBlockPos(), doDrain)

fun IFluidBlock.canDrain(position: BlockPosition): Boolean =
    canDrain(position.world!!, position.toBlockPos())

fun IFluidBlock.getFilledPercentage(position: BlockPosition): Float =
    getFilledPercentage(position.world!!, position.toBlockPos())
