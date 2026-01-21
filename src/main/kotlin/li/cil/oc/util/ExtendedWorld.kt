package li.cil.oc.util

import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import kotlin.math.max

/**
 * Wrapper object that provides extension-like methods on World.
 * Use via: import li.cil.oc.util.ExtendedWorld.extendedWorld
 * Then call: world.extendedWorld().someMethod()
 */
object ExtendedWorld {
    @JvmStatic
    fun World.extendedWorld(): ExtendedWorldWrapper = ExtendedWorldWrapper(this)

    @JvmStatic
    fun IBlockAccess.extendedBlockAccess(): ExtendedBlockAccessWrapper = ExtendedBlockAccessWrapper(this)
}

class ExtendedWorldWrapper(private val world: World) {
    fun blockExists(position: BlockPosition): Boolean =
        world.isBlockLoaded(position.toBlockPos())

    fun getBlock(position: BlockPosition): Block? =
        if (world.isBlockLoaded(position.toBlockPos()))
            world.getBlockState(position.toBlockPos()).block
        else null

    fun getBlockMetadata(position: BlockPosition): IBlockState =
        world.getBlockState(position.toBlockPos())

    fun isSideSolid(position: BlockPosition, side: EnumFacing): Boolean =
        world.isSideSolid(position.toBlockPos(), side)

    fun notifyBlockUpdate(pos: BlockPos) =
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)

    fun notifyBlockUpdate(position: BlockPosition) =
        notifyBlockUpdate(position.toBlockPos())
}

class ExtendedBlockAccessWrapper(private val blockAccess: IBlockAccess) {
    fun getBlock(position: BlockPosition): Block =
        blockAccess.getBlockState(position.toBlockPos()).block

    fun getBlockMetadata(position: BlockPosition): IBlockState =
        blockAccess.getBlockState(position.toBlockPos())

    fun getTileEntity(position: BlockPosition): TileEntity? =
        blockAccess.getTileEntity(position.toBlockPos())
}

// IBlockAccess extensions
fun IBlockAccess.getBlockState(position: BlockPosition): IBlockState =
    getBlockState(position.toBlockPos())

fun IBlockAccess.getBlock(position: BlockPosition): Block =
    this.getBlockState(position).block

fun IBlockAccess.getBlockHardness(position: BlockPosition): Float {
    val pos = position.toBlockPos()
    return getBlockState(pos).getBlockHardness(position.world!!, pos)
}

fun IBlockAccess.getBlockMapColor(position: BlockPosition) =
    getBlockMetadata(position).getMapColor(this, position.toBlockPos())

fun IBlockAccess.getBlockMetadata(position: BlockPosition): IBlockState =
    getBlockState(position.toBlockPos())

fun IBlockAccess.getTileEntity(position: BlockPosition): TileEntity? =
    getTileEntity(position.toBlockPos())

fun IBlockAccess.getTileEntity(host: EnvironmentHost): TileEntity? =
    getTileEntity(BlockPosition.invoke(host))

fun IBlockAccess.isAirBlock(position: BlockPosition): Boolean =
    isAirBlock(position.toBlockPos())

fun IBlockAccess.getLightBrightnessForSkyBlocks(position: BlockPosition, minBrightness: Int): Int =
    getCombinedLight(position.toBlockPos(), minBrightness)

// World extensions
fun World.blockExists(position: BlockPosition): Boolean =
    isBlockLoaded(position.toBlockPos())

fun World.breakBlock(position: BlockPosition, drops: Boolean = true): Boolean =
    destroyBlock(position.toBlockPos(), drops)

fun World.destroyBlockInWorldPartially(entityId: Int, position: BlockPosition, progress: Int) =
    sendBlockBreakProgress(entityId, position.toBlockPos(), progress)

fun World.extinguishFire(player: EntityPlayer?, position: BlockPosition, side: EnumFacing): Boolean =
    extinguishFire(player, position.toBlockPos(), side)

fun World.getBlockHardness(position: BlockPosition): Float =
    getBlock(position).getBlockHardness(getBlockState(position.toBlockPos()), this, position.toBlockPos())

fun World.getBlockHarvestLevel(position: BlockPosition): Int =
    getBlock(position).getHarvestLevel(getBlockMetadata(position))

fun World.getBlockHarvestTool(position: BlockPosition): String? =
    getBlock(position).getHarvestTool(getBlockMetadata(position))

fun World.computeRedstoneSignal(position: BlockPosition, side: EnumFacing): Int =
    max(isBlockProvidingPowerTo(position.offset(side), side), getIndirectPowerLevelTo(position.offset(side), side))

fun World.isBlockProvidingPowerTo(position: BlockPosition, side: EnumFacing): Int =
    getStrongPower(position.toBlockPos(), side)

fun World.getIndirectPowerLevelTo(position: BlockPosition, side: EnumFacing): Int =
    getRedstonePower(position.toBlockPos(), side)

fun World.notifyBlockUpdate(pos: BlockPos) =
    notifyBlockUpdate(pos, getBlockState(pos), getBlockState(pos), 3)

fun World.notifyBlockUpdate(position: BlockPosition) =
    notifyBlockUpdate(position, getBlockState(position.toBlockPos()), getBlockState(position.toBlockPos()))

fun World.notifyBlockUpdate(position: BlockPosition, oldState: IBlockState, newState: IBlockState, flags: Int = 3) =
    notifyBlockUpdate(position.toBlockPos(), oldState, newState, flags)

fun World.notifyBlockOfNeighborChange(position: BlockPosition, block: Block) =
    neighborChanged(position.toBlockPos(), block, position.toBlockPos())

fun World.notifyBlocksOfNeighborChange(position: BlockPosition, block: Block, updateObservers: Boolean) =
    notifyNeighborsOfStateChange(position.toBlockPos(), block, updateObservers)

fun World.notifyBlocksOfNeighborChange(position: BlockPosition, block: Block, side: EnumFacing) =
    notifyNeighborsOfStateExcept(position.toBlockPos(), block, side)

fun World.playAuxSFX(id: Int, position: BlockPosition, data: Int) =
    playEvent(id, position.toBlockPos(), data)

fun World.setBlock(position: BlockPosition, block: Block): Boolean =
    setBlockState(position.toBlockPos(), block.defaultState)

fun World.setBlock(position: BlockPosition, block: Block, metadata: Int, flag: Int): Boolean =
    setBlockState(position.toBlockPos(), block.getStateFromMeta(metadata), flag)

fun World.setBlockToAir(position: BlockPosition): Boolean =
    setBlockToAir(position.toBlockPos())

fun World.isSideSolid(position: BlockPosition, side: EnumFacing): Boolean =
    isSideSolid(position.toBlockPos(), side)

fun World.isBlockLoaded(position: BlockPosition): Boolean =
    isBlockLoaded(position.toBlockPos())