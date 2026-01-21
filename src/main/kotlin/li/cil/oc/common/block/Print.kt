package li.cil.oc.common.block

import li.cil.oc.Localization
import li.cil.oc.Settings
import li.cil.oc.common.block.property.PropertyTile
import li.cil.oc.common.block.traits.CustomDrops
import li.cil.oc.common.item.data.PrintData
import li.cil.oc.common.tileentity.Print as TEPrint
import li.cil.oc.integration.util.ItemBlacklist
import li.cil.oc.util.InventoryUtils
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLiving.SpawnPlacementType
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.BlockRenderLayer
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.property.ExtendedBlockState
import net.minecraftforge.common.property.IExtendedBlockState
import java.util.Random
import kotlin.reflect.KClass

class Print(protected val tileTag: KClass<TEPrint> = TEPrint::class) : RedstoneAware(), CustomDrops<TEPrint> {
    init {
        setLightOpacity(1)
        setHardness(1f)
        setCreativeTab(null)
        ItemBlacklist.hide(this)
    }

    // ----------------------------------------------------------------------- //

    override fun createBlockState() = ExtendedBlockState(this, emptyArray(), arrayOf(PropertyTile))

    override fun getExtendedState(state: IBlockState, world: IBlockAccess, pos: BlockPos): IBlockState {
        val tileEntity = world.getTileEntity(pos)
        return if (state is IExtendedBlockState && tileEntity is TEPrint) {
            state.withProperty(PropertyTile, tileEntity)
        } else state
    }

    // ----------------------------------------------------------------------- //

    override fun canRenderInLayer(state: IBlockState, layer: BlockRenderLayer): Boolean = layer == BlockRenderLayer.CUTOUT_MIPPED

    override fun tooltipBody(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, advanced: ITooltipFlag) {
        super.tooltipBody(metadata, stack, world, tooltip, advanced)
        val data = PrintData(stack)
        data.tooltip?.let { s ->
            s.lines().forEach { line -> tooltip.add(line) }
        }
    }

    override fun tooltipTail(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, advanced: ITooltipFlag) {
        super.tooltipTail(metadata, stack, world, tooltip, advanced)
        val data = PrintData(stack)
        if (data.isBeaconBase) {
            tooltip.add(Localization.Tooltip.PrintBeaconBase)
        }
        if (data.emitRedstone) {
            tooltip.add(Localization.Tooltip.PrintRedstoneLevel(data.redstoneLevel))
        }
        if (data.emitLight) {
            tooltip.add(Localization.Tooltip.PrintLightValue(data.lightLevel))
        }
    }

    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun getLightValue(state: IBlockState, world: IBlockAccess, pos: BlockPos): Int {
        return if (world is World && world.isBlockLoaded(pos)) {
            val tileEntity = world.getTileEntity(pos)
            if (tileEntity is TEPrint) tileEntity.data.lightLevel else super.getLightValue(state, world, pos)
        } else super.getLightValue(state, world, pos)
    }

    override fun getLightOpacity(state: IBlockState, world: IBlockAccess, pos: BlockPos): Int {
        return if (world is World && world.isBlockLoaded(pos)) {
            val tileEntity = world.getTileEntity(pos)
            if (tileEntity is TEPrint && Settings.get.printsHaveOpacity) {
                (tileEntity.data.opacity * 4).toInt()
            } else super.getLightOpacity(state, world, pos)
        } else super.getLightOpacity(state, world, pos)
    }

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun shouldSideBeRendered(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = true

    override fun isBlockSolid(world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean {
        val tileEntity = world.getTileEntity(pos)
        return tileEntity is TEPrint && tileEntity.isSideSolid(side)
    }

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean =
        isBlockSolid(world, pos, side)

    override fun getPickBlock(state: IBlockState, target: RayTraceResult, world: World, pos: BlockPos, player: EntityPlayer): ItemStack {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TEPrint) tileEntity.data.createItemStack() else ItemStack.EMPTY
    }

    override fun getBoundingBox(state: IBlockState, world: IBlockAccess, pos: BlockPos): AxisAlignedBB {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TEPrint) tileEntity.bounds else super.getBoundingBox(state, world, pos)
    }

    override fun addCollisionBoxToList(state: IBlockState, world: World, pos: BlockPos, mask: AxisAlignedBB, list: MutableList<AxisAlignedBB>, entity: Entity?, par7: Boolean) {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TEPrint) {
            tileEntity.addCollisionBoxesToList(mask, list, pos)
        } else {
            super.addCollisionBoxToList(state, world, pos, mask, list, entity, par7)
        }
    }

    override fun collisionRayTrace(state: IBlockState, world: World, pos: BlockPos, start: Vec3d, end: Vec3d): RayTraceResult? {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TEPrint) tileEntity.rayTrace(start, end, pos) else super.collisionRayTrace(state, world, pos, start, end)
    }

    override fun canCreatureSpawn(state: IBlockState, world: IBlockAccess, pos: BlockPos, type: SpawnPlacementType): Boolean = true

    override fun tickRate(world: World): Int = 20

    override fun updateTick(world: World, pos: BlockPos, state: IBlockState, rand: Random) {
        if (!world.isRemote) {
            val tileEntity = world.getTileEntity(pos)
            if (tileEntity is TEPrint) {
                if (tileEntity.state) tileEntity.toggleState()
                if (tileEntity.state) world.scheduleUpdate(pos, state.block, tickRate(world))
            }
        }
    }

    override fun isBeaconBase(world: IBlockAccess, pos: BlockPos, beacon: BlockPos): Boolean {
        val tileEntity = world.getTileEntity(pos)
        return tileEntity is TEPrint && tileEntity.data.isBeaconBase
    }

    // ----------------------------------------------------------------------- //

    override fun createNewTileEntity(worldIn: World, meta: Int) = TEPrint()

    // ----------------------------------------------------------------------- //

    override fun onBlockActivated(world: World, pos: BlockPos, state: IBlockState, player: EntityPlayer, hand: EnumHand, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is TEPrint) tileEntity.activate() else super.onBlockActivated(world, pos, state, player, hand, side, hitX, hitY, hitZ)
    }

    override fun doCustomInit(tileEntity: TEPrint, player: EntityLivingBase, stack: ItemStack) {
        super.doCustomInit(tileEntity, player, stack)
        tileEntity.data.load(stack)
        tileEntity.updateBounds()
        tileEntity.updateRedstone()
        tileEntity.world.checkLight(tileEntity.pos)
    }

    override fun doCustomDrops(tileEntity: TEPrint, player: EntityPlayer, willHarvest: Boolean) {
        super.doCustomDrops(tileEntity, player, willHarvest)
        if (!player.capabilities.isCreativeMode) {
            InventoryUtils.spawnStackInWorld(tileEntity.position, tileEntity.data.createItemStack())
        }
    }

    override fun breakBlock(world: World, pos: BlockPos, state: IBlockState) {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TEPrint && tileEntity.data.emitRedstone(tileEntity.state)) {
            world.notifyNeighborsOfStateChange(pos, this, false)
            for (side in EnumFacing.values()) {
                world.notifyNeighborsOfStateChange(pos.offset(side), this, false)
            }
        }
        super.breakBlock(world, pos, state)
    }

    override val tileEntityClass: Class<TEPrint> get() = TEPrint::class.java
}
