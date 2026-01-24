package li.cil.oc.common.block

import li.cil.oc.CreativeTab
import li.cil.oc.common.tileentity.traits.Colored
import li.cil.oc.common.tileentity.traits.Inventory
import li.cil.oc.common.tileentity.traits.Rotatable
import li.cil.oc.util.Color
import li.cil.oc.util.Tooltip
import net.minecraft.block.BlockContainer
import net.minecraft.block.material.Material
import net.minecraft.block.state.BlockFaceShape
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLiving.SpawnPlacementType
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.EnumDyeColor
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.*
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

abstract class SimpleBlock(material: Material = Material.IRON) : BlockContainer(material) {
    init {
        setHardness(2f)
        setResistance(5f)
        setCreativeTab(CreativeTab)
    }

    @JvmField
    var showInItemList = true

    protected val validRotations_ = arrayOf(EnumFacing.UP, EnumFacing.DOWN)

    open fun createItemStack(amount: Int = 1): ItemStack = ItemStack(this, amount)

    override fun createNewTileEntity(world: World, meta: Int): TileEntity? = null

    @SideOnly(Side.CLIENT)
    override fun shouldSideBeRendered(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean {
        val bounds = getBoundingBox(state, world, pos)
        return (side == EnumFacing.DOWN && bounds.minY > 0) ||
            (side == EnumFacing.UP && bounds.maxY < 1) ||
            (side == EnumFacing.NORTH && bounds.minZ > 0) ||
            (side == EnumFacing.SOUTH && bounds.maxZ < 1) ||
            (side == EnumFacing.WEST && bounds.minX > 0) ||
            (side == EnumFacing.EAST && bounds.maxX < 1) ||
            isOpaqueCube(state)
    }

    // ----------------------------------------------------------------------- //
    // Rendering
    // ----------------------------------------------------------------------- //

    override fun getRenderType(state: IBlockState): EnumBlockRenderType = EnumBlockRenderType.MODEL

    @SideOnly(Side.CLIENT)
    open fun preItemRender(metadata: Int) {}

    // ----------------------------------------------------------------------- //
    // ItemBlock
    // ----------------------------------------------------------------------- //

    open fun rarity(stack: ItemStack): EnumRarity = EnumRarity.COMMON

    @SideOnly(Side.CLIENT)
    open fun addInformation(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, flag: ITooltipFlag) {
        tooltipHead(metadata, stack, world, tooltip, flag)
        tooltipBody(metadata, stack, world, tooltip, flag)
        tooltipTail(metadata, stack, world, tooltip, flag)
    }

    protected open fun tooltipHead(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, flag: ITooltipFlag) {}

    protected open fun tooltipBody(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, flag: ITooltipFlag) {
        tooltip.addAll(Tooltip.get(javaClass.simpleName.lowercase()))
    }

    protected open fun tooltipTail(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, flag: ITooltipFlag) {}

    // ----------------------------------------------------------------------- //
    // Rotation
    // ----------------------------------------------------------------------- //

    open fun getFacing(world: IBlockAccess, pos: BlockPos): EnumFacing {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is Rotatable) tileEntity.facing() ?: EnumFacing.SOUTH else EnumFacing.SOUTH
    }

    open fun setFacing(world: World, pos: BlockPos, value: EnumFacing): Boolean {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is Rotatable) {
            tileEntity.setFromFacing(value)
            true
        } else false
    }

    open fun setRotationFromEntityPitchAndYaw(world: World, pos: BlockPos, value: Entity): Boolean {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is Rotatable) {
            tileEntity.setFromEntityPitchAndYaw(value)
            true
        } else false
    }

    open fun toLocal(world: IBlockAccess, pos: BlockPos, value: EnumFacing): EnumFacing {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is Rotatable) tileEntity.toLocal(value) else value
    }

    // ----------------------------------------------------------------------- //
    // Block
    // ----------------------------------------------------------------------- //

    override fun getBlockFaceShape(world: IBlockAccess, state: IBlockState, pos: BlockPos, side: EnumFacing): BlockFaceShape =
        if (isBlockSolid(world, pos, side)) BlockFaceShape.SOLID else BlockFaceShape.UNDEFINED

    open fun isBlockSolid(world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = world.getBlockState(pos).material.isSolid

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = true

    override fun canHarvestBlock(world: IBlockAccess, pos: BlockPos, player: EntityPlayer): Boolean = true

    override fun getHarvestTool(state: IBlockState): String? = null

    override fun canBeReplacedByLeaves(state: IBlockState, world: IBlockAccess, pos: BlockPos): Boolean = false

    override fun canCreatureSpawn(state: IBlockState, world: IBlockAccess, pos: BlockPos, type: SpawnPlacementType): Boolean = false

    override fun getValidRotations(world: World, pos: BlockPos): Array<EnumFacing> = validRotations_

    override fun breakBlock(world: World, pos: BlockPos, state: IBlockState) {
        if (!world.isRemote) {
            val tileEntity = world.getTileEntity(pos)
            if (tileEntity is Inventory) {
                tileEntity.dropAllSlots()
            }
        }
        super.breakBlock(world, pos, state)
    }

    // ----------------------------------------------------------------------- //

    override fun rotateBlock(world: World, pos: BlockPos, axis: EnumFacing): Boolean {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is li.cil.oc.common.tileentity.traits.Rotatable && tileEntity.rotate(axis)) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
            true
        } else false
    }

    override fun recolorBlock(world: World, pos: BlockPos, side: EnumFacing, color: EnumDyeColor): Boolean {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is Colored && tileEntity.getColor().toUInt() != Color.rgbValues(color)) {
            tileEntity.setColor(Color.rgbValues(color).toInt())
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
            true // Blame Vexatos.
        } else super.recolorBlock(world, pos, side, color)
    }

    // ----------------------------------------------------------------------- //

    override fun onBlockActivated(world: World, pos: BlockPos, state: IBlockState, player: EntityPlayer, hand: EnumHand, facing: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        val heldItem = player.getHeldItem(hand)
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is Colored && Color.isDye(heldItem)) {
            tileEntity.setColor(Color.rgbValues(Color.dyeColor(heldItem)).toInt())
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3)
            if (!player.capabilities.isCreativeMode && tileEntity.consumesDye) {
                heldItem.splitStack(1)
            }
            true
        } else localOnBlockActivated(world, pos, player, hand, heldItem, facing, hitX, hitY, hitZ)
    }

    open fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean = false
}
