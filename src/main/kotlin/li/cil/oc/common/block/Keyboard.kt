package li.cil.oc.common.block

import li.cil.oc.Constants
import li.cil.oc.api.Network
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import java.util.Random
import li.cil.oc.common.tileentity.Keyboard as TEKeyboard
import li.cil.oc.common.tileentity.Screen as TEScreen

class Keyboard : SimpleBlock(Material.ROCK) {
    init {
        setLightOpacity(0)
    }

    // For Immibis Microblock support.
    @JvmField
    val ImmibisMicroblocks_TransformableBlockMarker: Any? = null

    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, PropertyRotatable.Pitch, PropertyRotatable.Yaw)

    override fun getMetaFromState(state: IBlockState): Int = (state.getValue(PropertyRotatable.Pitch).ordinal shl 2) or state.getValue(PropertyRotatable.Yaw).horizontalIndex

    override fun getStateFromMeta(meta: Int): IBlockState = defaultState.withProperty(PropertyRotatable.Pitch, EnumFacing.byIndex(meta shr 2)).withProperty(PropertyRotatable.Yaw, EnumFacing.byHorizontalIndex(meta and 0x3))

    // ----------------------------------------------------------------------- //

    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = false

    override fun getBoundingBox(state: IBlockState, world: IBlockAccess, pos: BlockPos): AxisAlignedBB {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TEKeyboard) {
            val pitch = tileEntity.pitch
            val yaw = tileEntity.yaw
            val (forward, up) = when (pitch) {
                EnumFacing.DOWN, EnumFacing.UP -> Pair(pitch, yaw)
                else -> Pair(yaw, EnumFacing.UP)
            }
            val side = ExtendedEnumFacing.getRotation(forward, up)
            val sizes = floatArrayOf(7f / 16f, 4f / 16f, 7f / 16f)
            val x0 = -up.xOffset * sizes[1] - side.xOffset * sizes[2] - forward.xOffset * sizes[0]
            val x1 = up.xOffset * sizes[1] + side.xOffset * sizes[2] - forward.xOffset * 0.5f
            val y0 = -up.yOffset * sizes[1] - side.yOffset * sizes[2] - forward.yOffset * sizes[0]
            val y1 = up.yOffset * sizes[1] + side.yOffset * sizes[2] - forward.yOffset * 0.5f
            val z0 = -up.zOffset * sizes[1] - side.zOffset * sizes[2] - forward.zOffset * sizes[0]
            val z1 = up.zOffset * sizes[1] + side.zOffset * sizes[2] - forward.zOffset * 0.5f
            AxisAlignedBB(x0.toDouble(), y0.toDouble(), z0.toDouble(), x1.toDouble(), y1.toDouble(), z1.toDouble()).offset(0.5, 0.5, 0.5)
        } else super.getBoundingBox(state, world, pos)
    }

    // ----------------------------------------------------------------------- //

    override fun shouldSideBeRendered(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = true

    override fun preItemRender(metadata: Int) {
        GlStateManager.translate(-0.75f, 0f, 0f)
        GlStateManager.scale(1.5f, 1.5f, 1.5f)
    }

    // ----------------------------------------------------------------------- //

    override fun createNewTileEntity(world: World, metadata: Int) = TEKeyboard()

    // ----------------------------------------------------------------------- //

    override fun updateTick(world: World, pos: BlockPos, state: IBlockState, rand: Random) {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TEKeyboard) {
            Network.joinOrCreateNetwork(tileEntity)
        }
    }

    override fun canPlaceBlockOnSide(world: World, pos: BlockPos, side: EnumFacing): Boolean {
        if (!world.isSideSolid(pos.offset(side.opposite), side)) return false
        val tileEntity = world.getTileEntity(pos.offset(side.opposite))
        return if (tileEntity is TEScreen) tileEntity.facing != side else true
    }

    override fun neighborChanged(state: IBlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos) {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TEKeyboard) {
            if (!canPlaceBlockOnSide(world, pos, tileEntity.facing)) {
                world.setBlockToAir(pos)
                InventoryUtils.spawnStackInWorld(BlockPosition(pos, world), api.Items.get(Constants.BlockName.Keyboard).createItemStack(1))
            }
        }
    }

    override fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        val info = adjacencyInfo(world, pos)
        return if (info != null) {
            info.second.rightClick(world, info.third, player, hand, heldItem, info.fourth, 0f, 0f, 0f, force = true)
        } else false
    }

    fun adjacencyInfo(world: World, pos: BlockPos): AdjacentScreenInfo? {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TEKeyboard) {
            val blockPos = pos.offset(tileEntity.facing.opposite)
            val block = world.getBlockState(blockPos).block
            if (block is Screen) {
                return AdjacentScreenInfo(tileEntity, block, blockPos, tileEntity.facing.opposite)
            }
            // Special case #1: check for screen in front of the keyboard.
            val forward = when (tileEntity.facing) {
                EnumFacing.UP, EnumFacing.DOWN -> tileEntity.yaw
                else -> EnumFacing.UP
            }
            val blockPos2 = pos.offset(forward)
            val block2 = world.getBlockState(blockPos2).block
            if (block2 is Screen) {
                return AdjacentScreenInfo(tileEntity, block2, blockPos2, forward)
            }
            if (tileEntity.facing != EnumFacing.UP && tileEntity.facing != EnumFacing.DOWN) {
                // Special case #2: check for screen below keyboards on walls.
                val blockPos3 = pos.offset(forward.opposite)
                val block3 = world.getBlockState(blockPos3).block
                if (block3 is Screen) {
                    return AdjacentScreenInfo(tileEntity, block3, blockPos3, forward.opposite)
                }
            }
        }
        return null
    }

    override fun getValidRotations(world: World, pos: BlockPos): Array<EnumFacing>? = null

    data class AdjacentScreenInfo(val first: TEKeyboard, val second: Screen, val third: BlockPos, val fourth: EnumFacing)
}
