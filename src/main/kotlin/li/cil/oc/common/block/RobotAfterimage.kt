package li.cil.oc.common.block

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.common.item.data.RobotData
import li.cil.oc.common.tileentity.Robot as TERobot
import li.cil.oc.common.tileentity.RobotProxy as TERobotProxy
import li.cil.oc.integration.util.ItemBlacklist
import li.cil.oc.util.Rarity
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3i
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import java.util.Random

class RobotAfterimage : SimpleBlock() {
    init {
        setLightOpacity(0)
        setCreativeTab(null)
        ItemBlacklist.hide(this)
    }

    // ----------------------------------------------------------------------- //

    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun shouldSideBeRendered(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = false

    override fun isBlockSolid(world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = false

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = false

    override fun getPickBlock(state: IBlockState, target: RayTraceResult, world: World, pos: BlockPos, player: EntityPlayer): ItemStack {
        val robot = findMovingRobot(world, pos)
        return robot?.info?.createItemStack() ?: ItemStack.EMPTY
    }

    override fun getBoundingBox(state: IBlockState, world: IBlockAccess, pos: BlockPos): AxisAlignedBB {
        val robot = findMovingRobot(world, pos)
        return if (robot != null) {
            val block = robot.getBlockType() as SimpleBlock
            val bounds = block.getBoundingBox(state, world, robot.pos)
            val delta = robot.moveFrom?.let { vec ->
                val blockPos = robot.pos
                BlockPos(blockPos.x - vec.x, blockPos.y - vec.y, blockPos.z - vec.z)
            } ?: Vec3i.NULL_VECTOR
            bounds.offset(delta.x.toDouble(), delta.y.toDouble(), delta.z.toDouble())
        } else super.getBoundingBox(state, world, pos)
    }

    // ----------------------------------------------------------------------- //

    override fun hasTileEntity(state: IBlockState): Boolean = false

    override fun createNewTileEntity(worldIn: World, meta: Int) = null

    // ----------------------------------------------------------------------- //

    override fun rarity(stack: ItemStack): net.minecraft.item.EnumRarity {
        val data = RobotData(stack)
        return Rarity.byTier(data.tier)
    }

    override fun isAir(state: IBlockState, world: IBlockAccess, pos: BlockPos): Boolean = true

    // ----------------------------------------------------------------------- //

    override fun onBlockAdded(world: World, pos: BlockPos, state: IBlockState) {
        world.scheduleUpdate(pos, this, maxOf((Settings.get.moveDelay * 20).toInt(), 1) - 1)
    }

    override fun updateTick(world: World, pos: BlockPos, state: IBlockState, rand: Random) {
        world.setBlockToAir(pos)
    }

    override fun removedByPlayer(state: IBlockState, world: World, pos: BlockPos, player: EntityPlayer, willHarvest: Boolean): Boolean {
        val robot = findMovingRobot(world, pos)
        return if (robot != null && robot.isAnimatingMove && robot.moveFrom?.equals(pos) == true) {
            robot.proxy.getBlockType().removedByPlayer(state, world, pos, player, false)
        } else super.removedByPlayer(state, world, pos, player, willHarvest) // Probably broken by the robot we represent.
    }

    override fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        val robot = findMovingRobot(world, pos)
        return if (robot != null) {
            Constants.BlockInfo.Robot.block()!!.onBlockActivated(world, robot.pos, world.getBlockState(robot.pos), player, hand, side, hitX, hitY, hitZ)
        } else {
            world.setBlockToAir(pos)
            false
        }
    }

    fun findMovingRobot(world: IBlockAccess, pos: BlockPos): TERobot? {
        for (side in EnumFacing.values()) {
            val tpos = pos.offset(side)
            val isLoaded = if (world is World) world.isBlockLoaded(tpos) else true
            if (isLoaded) {
                val tileEntity = world.getTileEntity(tpos)
                if (tileEntity is TERobotProxy && tileEntity.robot.moveFrom?.equals(pos) == true) {
                    return tileEntity.robot
                }
            }
        }
        return null
    }
}
