package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.api.component.RackMountable
import li.cil.oc.common.GuiType
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.block.property.PropertyTile
import li.cil.oc.common.tileentity.Rack as TERack
import li.cil.oc.common.tileentity.TileEntity as TETileEntity
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumFacing.Axis
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.property.ExtendedBlockState
import net.minecraftforge.common.property.IExtendedBlockState
import li.cil.oc.common.block.traits.PowerAcceptor as TraitPowerAcceptor
import li.cil.oc.common.block.traits.StateAware as TraitStateAware
import li.cil.oc.common.block.traits.GUI as TraitGUI

class Rack : RedstoneAware(), TraitPowerAcceptor, TraitStateAware, TraitGUI {
    override fun createBlockState() = ExtendedBlockState(this, arrayOf(PropertyRotatable.Facing), arrayOf(PropertyTile))

    override fun getStateFromMeta(meta: Int): IBlockState = defaultState.withProperty(PropertyRotatable.Facing, EnumFacing.byHorizontalIndex(meta))

    override fun getMetaFromState(state: IBlockState): Int = state.getValue(PropertyRotatable.Facing).horizontalIndex

    override fun getExtendedState(state: IBlockState, world: IBlockAccess, pos: BlockPos): IBlockState {
        val tileEntity = world.getTileEntity(pos)
        val extendedState = if (state is IExtendedBlockState && tileEntity is TETileEntity) {
            state.withProperty(PropertyTile, tileEntity)
        } else state
        return extendedState.withProperty(PropertyRotatable.Facing, getFacing(world, pos))
    }

    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun isBlockSolid(world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = side == EnumFacing.SOUTH

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = toLocal(world, pos, side) != EnumFacing.SOUTH

    // ----------------------------------------------------------------------- //

    override val energyThroughput: Double get() = Settings.get.serverRackRate

    override val guiType = GuiType.Rack

    override fun createNewTileEntity(world: World, metadata: Int) = TERack()

    // ----------------------------------------------------------------------- //

    val collisionBounds = arrayOf(
        AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0 / 16.0, 1.0),
        AxisAlignedBB(0.0, 15.0 / 16.0, 0.0, 1.0, 1.0, 1.0),
        AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0 / 16.0),
        AxisAlignedBB(0.0, 0.0, 15.0 / 16.0, 1.0, 1.0, 1.0),
        AxisAlignedBB(0.0, 0.0, 0.0, 1.0 / 16.0, 1.0, 1.0),
        AxisAlignedBB(15.0 / 16.0, 0.0, 0.0, 1.0, 1.0, 1.0),
        AxisAlignedBB(0.5 / 16.0, 0.5 / 16.0, 0.5 / 16.0, 15.5 / 16.0, 15.5 / 16.0, 15.5 / 16.0)
    )

    override fun collisionRayTrace(state: IBlockState, world: World, pos: BlockPos, start: Vec3d, end: Vec3d): RayTraceResult? {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TERack) {
            var closestDistance = Double.POSITIVE_INFINITY
            var closest: RayTraceResult? = null

            fun intersect(bounds: AxisAlignedBB) {
                val hit = bounds.offset(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble()).calculateIntercept(start, end)
                if (hit != null) {
                    val distance = hit.hitVec.distanceTo(start)
                    if (distance < closestDistance) {
                        closestDistance = distance
                        closest = hit
                    }
                }
            }

            val facings = EnumFacing.VALUES
            for (i in facings.indices) {
                if (tileEntity.facing() != facings[i]) {
                    intersect(collisionBounds[i])
                }
            }
            intersect(collisionBounds.last())
            return closest?.let { RayTraceResult(it.hitVec, it.sideHit, pos) }
        }
        return super.collisionRayTrace(state, world, pos, start, end)
    }

    override fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TERack) {
            val slot = tileEntity.slotAt(side, hitX, hitY, hitZ)
            if (slot != null) {
                // Snap to grid to get same behavior on client and server...
                val hitVec = Vec3d((hitX * 16f).toInt() / 16.0, (hitY * 16f).toInt() / 16.0, (hitZ * 16f).toInt() / 16.0)
                val rotation = when (side) {
                    EnumFacing.WEST -> Math.toRadians(90.0).toFloat()
                    EnumFacing.NORTH -> Math.toRadians(180.0).toFloat()
                    EnumFacing.EAST -> Math.toRadians(270.0).toFloat()
                    else -> 0f
                }
                // Rotate *centers* of pixels to keep association when reversing axis.
                val localHitVec = rotate(hitVec.add(-0.5 + 1.0 / 32.0, -0.5 + 1.0 / 32.0, -0.5 + 1.0 / 32.0), rotation).add(0.5 - 1.0 / 32.0, 0.5 - 1.0 / 32.0, 0.5 - 1.0 / 32.0)
                val globalX = (localHitVec.x * 16.05f).toInt() // [0, 15], work around floating point inaccuracies
                val globalY = (localHitVec.y * 16.05f).toInt() // [0, 15], work around floating point inaccuracies
                val localX = (if (side.axis != Axis.Z) 15 - globalX else globalX) - 1
                val localY = (15 - globalY) - 2 - 3 * slot
                if (localX >= 0 && localX < 14 && localY >= 0 && localY < 3) {
                    val mountable = tileEntity.getMountable(slot)
                    if (mountable is RackMountable && mountable.onActivate(player, hand, heldItem, localX / 14f, localY / 3f)) {
                        return true // Activation handled by mountable.
                    }
                }
            }
        }
        return super.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)
    }

    fun rotate(v: Vec3d, t: Float): Vec3d {
        val cos = Math.cos(t.toDouble())
        val sin = Math.sin(t.toDouble())
        return Vec3d(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos)
    }
}
