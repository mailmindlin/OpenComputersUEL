package li.cil.oc.common.block

import li.cil.oc.common.block.property.UnlistedInteger
import li.cil.oc.common.block.traits.CustomDrops
import li.cil.oc.common.capabilities.Capabilities
import li.cil.oc.common.tileentity.Cable as TECable
import li.cil.oc.util.Color
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.EnumDyeColor
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.property.ExtendedBlockState
import net.minecraftforge.common.property.IExtendedBlockState
import kotlin.reflect.KClass

class Cable(protected val tileTag: KClass<TECable> = TECable::class) : SimpleBlock(), CustomDrops<TECable> {
    // For Immibis Microblock support.
    @JvmField
    val ImmibisMicroblocks_TransformableBlockMarker: Any? = null

    // For FMP part coloring.
    @JvmField
    var colorMultiplierOverride: Int? = null

    // ----------------------------------------------------------------------- //

    override fun createBlockState() = ExtendedBlockState(this, emptyArray(), arrayOf(NeighborsProp, ColorProp, IsSideCableProp))

    override fun getExtendedState(state: IBlockState, world: IBlockAccess, pos: BlockPos): IBlockState {
        val tileEntity = world.getTileEntity(pos)
        return if (state is IExtendedBlockState && tileEntity is TECable) {
            var isCableMask = 0
            for (side in EnumFacing.values()) {
                if (world.getTileEntity(pos.offset(side)) is TECable) {
                    isCableMask = mask(side, isCableMask)
                }
            }
            state.withProperty(NeighborsProp, neighbors(world, pos))
                .withProperty(ColorProp, tileEntity.getColor())
                .withProperty(IsSideCableProp, isCableMask)
        } else state
    }

    // ----------------------------------------------------------------------- //

    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun shouldSideBeRendered(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = true

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = false

    // ----------------------------------------------------------------------- //

    override fun getPickBlock(state: IBlockState, target: RayTraceResult, world: World, pos: BlockPos, player: EntityPlayer): ItemStack {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TECable) tileEntity.createItemStack() else createItemStack()
    }

    override fun getBoundingBox(state: IBlockState, world: IBlockAccess, pos: BlockPos): AxisAlignedBB = bounds(world, pos)

    override fun addCollisionBoxToList(state: IBlockState, worldIn: World, pos: BlockPos, entityBox: AxisAlignedBB, collidingBoxes: MutableList<AxisAlignedBB>, entityIn: Entity?, isActualState: Boolean) {
        parts(worldIn, pos, entityBox, collidingBoxes)
    }

    override fun collisionRayTrace(state: IBlockState, world: World, pos: BlockPos, start: Vec3d, end: Vec3d): RayTraceResult? {
        var distance = Double.POSITIVE_INFINITY
        var result: RayTraceResult? = null

        val boxes = mutableListOf<AxisAlignedBB>()
        parts(world, pos, Block.FULL_BLOCK_AABB.offset(pos), boxes)
        for (part in boxes) {
            val hit = part.calculateIntercept(start, end)
            if (hit != null) {
                val hitDistance = hit.hitVec.squareDistanceTo(start)
                if (hitDistance < distance) {
                    distance = hitDistance
                    result = hit
                }
            }
        }

        return if (result == null) null else RayTraceResult(result.hitVec, result.sideHit, pos)
    }

    // ----------------------------------------------------------------------- //

    override fun createNewTileEntity(world: World, metadata: Int) = TECable()

    // ----------------------------------------------------------------------- //

    override fun neighborChanged(state: IBlockState, world: World, pos: BlockPos, neighborBlock: Block, sourcePos: BlockPos) {
        world.notifyBlockUpdate(pos, state, state, 3)
        super.neighborChanged(state, world, pos, neighborBlock, sourcePos)
    }

    override fun doCustomInit(tileEntity: TECable, player: EntityLivingBase, stack: ItemStack) {
        super.doCustomInit(tileEntity, player, stack)
        if (!tileEntity.world.isRemote) {
            tileEntity.fromItemStack(stack)
        }
    }

    override fun doCustomDrops(tileEntity: TECable, player: EntityPlayer, willHarvest: Boolean) {
        super.doCustomDrops(tileEntity, player, willHarvest)
        if (!player.capabilities.isCreativeMode) {
            Block.spawnAsEntity(tileEntity.world, tileEntity.pos, tileEntity.createItemStack())
        }
    }

    override val tileEntityClass: Class<TECable> get() = TECable::class.java

    companion object {
        const val MIN = 0.375
        const val MAX = 1 - MIN

        @JvmField
        val DefaultBounds: AxisAlignedBB = AxisAlignedBB(MIN, MIN, MIN, MAX, MAX, MAX)

        @JvmField
        val CachedParts: Array<AxisAlignedBB> = arrayOf(
            AxisAlignedBB(MIN, 0.0, MIN, MAX, MIN, MAX), // Down
            AxisAlignedBB(MIN, MAX, MIN, MAX, 1.0, MAX), // Up
            AxisAlignedBB(MIN, MIN, 0.0, MAX, MAX, MIN), // North
            AxisAlignedBB(MIN, MIN, MAX, MAX, MAX, 1.0), // South
            AxisAlignedBB(0.0, MIN, MIN, MIN, MAX, MAX), // West
            AxisAlignedBB(MAX, MIN, MIN, 1.0, MAX, MAX)  // East
        )

        @JvmField
        val CachedBounds: Array<AxisAlignedBB> = (0..(0xFF shr 2)).map { mask ->
            EnumFacing.VALUES.fold(DefaultBounds) { bound, side ->
                if (((1 shl side.index) and mask) != 0) bound.union(CachedParts[side.ordinal])
                else bound
            }
        }.toTypedArray()

        @JvmField
        val NeighborsProp = UnlistedInteger("neighbors")
        @JvmField
        val ColorProp = UnlistedInteger("color")
        @JvmField
        val IsSideCableProp = UnlistedInteger("is_cable")

        @JvmStatic
        fun mask(side: EnumFacing, value: Int = 0): Int = value or (1 shl side.index)

        @JvmStatic
        fun neighbors(world: IBlockAccess, pos: BlockPos): Int {
            var result = 0
            val tileEntity = world.getTileEntity(pos)
            for (side in EnumFacing.values()) {
                val tpos = pos.offset(side)
                val hasNode = hasNetworkNode(tileEntity, side)
                if (hasNode && (if (world is World) world.isBlockLoaded(tpos) else !world.isAirBlock(tpos))) {
                    val neighborTileEntity = world.getTileEntity(tpos)
                    if (neighborTileEntity != null && neighborTileEntity.world != null) {
                        val neighborHasNode = hasNetworkNode(neighborTileEntity, side.opposite)
                        val canConnectColor = canConnectBasedOnColor(tileEntity, neighborTileEntity)
                        val canConnectIM = canConnectFromSideIM(tileEntity, side) && canConnectFromSideIM(neighborTileEntity, side.opposite)
                        if (neighborHasNode && canConnectColor && canConnectIM) {
                            result = mask(side, result)
                        }
                    }
                }
            }
            return result
        }

        @JvmStatic
        fun bounds(world: IBlockAccess, pos: BlockPos): AxisAlignedBB = CachedBounds[neighbors(world, pos)]

        @JvmStatic
        fun parts(world: IBlockAccess, pos: BlockPos, entityBox: AxisAlignedBB, boxes: MutableList<AxisAlignedBB>) {
            val center = DefaultBounds.offset(pos)
            if (entityBox.intersects(center)) boxes.add(center)

            val mask = neighbors(world, pos)
            for (side in EnumFacing.VALUES) {
                if (((1 shl side.index) and mask) != 0) {
                    val part = CachedParts[side.ordinal].offset(pos)
                    if (entityBox.intersects(part)) boxes.add(part)
                }
            }
        }

        private fun hasNetworkNode(tileEntity: TileEntity?, side: EnumFacing): Boolean {
            if (tileEntity != null) {
                if (tileEntity is li.cil.oc.common.tileentity.RobotProxy) return false

                if (tileEntity.hasCapability(Capabilities.SidedEnvironmentCapability, side)) {
                    val host = tileEntity.getCapability(Capabilities.SidedEnvironmentCapability, side)
                    if (host != null) {
                        return if (tileEntity.world.isRemote) host.canConnect(side) else host.sidedNode(side) != null
                    }
                }

                if (tileEntity.hasCapability(Capabilities.EnvironmentCapability, side)) {
                    val host = tileEntity.getCapability(Capabilities.EnvironmentCapability, side)
                    if (host != null) return true
                }
            }
            return false
        }

        private fun getConnectionColor(tileEntity: TileEntity?): UInt {
            if (tileEntity != null) {
                if (tileEntity.hasCapability(Capabilities.ColoredCapability, null)) {
                    val colored = tileEntity.getCapability(Capabilities.ColoredCapability, null)
                    if (colored != null && colored.controlsConnectivity()) return colored.color.toUInt()
                }
            }
            return Color.rgbValues(EnumDyeColor.SILVER)
        }

        private fun canConnectBasedOnColor(te1: TileEntity?, te2: TileEntity?): Boolean {
            val c1 = getConnectionColor(te1)
            val c2 = getConnectionColor(te2)
            return c1 == c2 || c1 == Color.rgbValues(EnumDyeColor.SILVER) || c2 == Color.rgbValues(EnumDyeColor.SILVER)
        }

        private fun canConnectFromSideIM(tileEntity: TileEntity?, side: EnumFacing): Boolean {
            return if (tileEntity is li.cil.oc.common.tileentity.traits.ImmibisMicroblock) {
                tileEntity.ImmibisMicroblocks_isSideOpen(side.ordinal)
            } else true
        }
    }
}
