package li.cil.oc.common.block

import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.common.GuiType
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.block.property.PropertyTile
import li.cil.oc.common.tileentity
import li.cil.oc.integration.util.Wrench
import li.cil.oc.util.PackedColor
import li.cil.oc.util.Rarity
import li.cil.oc.util.Tooltip
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.projectile.EntityArrow
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.property.ExtendedBlockState
import net.minecraftforge.common.property.IExtendedBlockState
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class Screen(val tier: Int) : RedstoneAware() {
    override fun createBlockState() = ExtendedBlockState(this, arrayOf(PropertyRotatable.Pitch, PropertyRotatable.Yaw), arrayOf(PropertyTile.Tile))

    override fun getMetaFromState(state: IBlockState): Int = (state.getValue(PropertyRotatable.Pitch).ordinal shl 2) or state.getValue(PropertyRotatable.Yaw).horizontalIndex

    override fun getStateFromMeta(meta: Int): IBlockState = defaultState.withProperty(PropertyRotatable.Pitch, EnumFacing.byIndex(meta shr 2)).withProperty(PropertyRotatable.Yaw, EnumFacing.byHorizontalIndex(meta and 0x3))

    override fun getExtendedState(state: IBlockState, world: IBlockAccess, pos: BlockPos): IBlockState {
        val tileEntity = world.getTileEntity(pos)
        return if (state is IExtendedBlockState && tileEntity is tileentity.Screen) {
            state
                .withProperty(property.PropertyTile.Tile, tileEntity)
                .withProperty(PropertyRotatable.Pitch, tileEntity.pitch)
                .withProperty(PropertyRotatable.Yaw, tileEntity.yaw)
        } else state
    }

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = toLocal(world, pos, side) != EnumFacing.SOUTH

    // ----------------------------------------------------------------------- //

    override fun rarity(stack: ItemStack) = Rarity.byTier(tier)

    override fun tooltipBody(metadata: Int, stack: ItemStack, world: World, tooltip: java.util.List<String>, advanced: ITooltipFlag) {
        val (w, h) = Settings.screenResolutionsByTier(tier)
        val depth = PackedColor.Depth.bits(Settings.screenDepthsByTier(tier))
        tooltip.addAll(Tooltip.get(javaClass.simpleName.lowercase(), w, h, depth))
    }

    // ----------------------------------------------------------------------- //

    override fun createNewTileEntity(world: World, metadata: Int) = tileentity.Screen(tier)

    // ----------------------------------------------------------------------- //

    override fun onBlockPlacedBy(world: World, pos: BlockPos, state: IBlockState, placer: EntityLivingBase, stack: ItemStack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack)
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is tileentity.Screen) {
            tileEntity.delayUntilCheckForMultiBlock = 0
        }
    }

    override fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean =
        rightClick(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ, force = false)

    fun rightClick(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack,
                   side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float, force: Boolean): Boolean {
        if (Wrench.holdsApplicableWrench(player, pos) && getValidRotations(world, pos).contains(side) && !force) return false
        if (api.Items.get(heldItem) == api.Items.get(Constants.ItemName.Analyzer)) return false

        val tileEntity = world.getTileEntity(pos)
        return when {
            tileEntity is tileentity.Screen && tileEntity.hasKeyboard && (force || player.isSneaking == tileEntity.origin.invertTouchMode) -> {
                // Yep, this GUI is actually purely client side. We could skip this
                // if, but it is clearer this way (to trigger it from the server we
                // would have to give screens a "container", which we do not want).
                if (world.isRemote) {
                    player.openGui(OpenComputers, GuiType.Screen.id, world, pos.x, pos.y, pos.z)
                }
                true
            }
            tileEntity is tileentity.Screen && tileEntity.tier > 0 && side == tileEntity.facing -> {
                if (world.isRemote && player == Minecraft.getMinecraft().player) {
                    tileEntity.click(hitX, hitY, hitZ)
                }
                true
            }
            else -> false
        }
    }

    override fun onEntityWalk(world: World, pos: BlockPos, entity: Entity) {
        if (!world.isRemote) {
            val tileEntity = world.getTileEntity(pos)
            if (tileEntity is tileentity.Screen && tileEntity.tier > 0 && tileEntity.facing == EnumFacing.UP) {
                tileEntity.walk(entity)
            } else {
                super.onEntityWalk(world, pos, entity)
            }
        }
    }

    override fun onEntityCollision(world: World, pos: BlockPos, state: IBlockState, entity: Entity) {
        if (world.isRemote) {
            val tileEntity = world.getTileEntity(pos)
            if (entity is EntityArrow && tileEntity is tileentity.Screen && tileEntity.tier > 0) {
                val hitX = maxOf(0.0, minOf(1.0, entity.posX - pos.x))
                val hitY = maxOf(0.0, minOf(1.0, entity.posY - pos.y))
                val hitZ = maxOf(0.0, minOf(1.0, entity.posZ - pos.z))
                val absX = Math.abs(hitX - 0.5)
                val absY = Math.abs(hitY - 0.5)
                val absZ = Math.abs(hitZ - 0.5)
                val side = when {
                    absX > absY && absX > absZ -> if (hitX < 0.5) EnumFacing.WEST else EnumFacing.EAST
                    absY > absZ -> if (hitY < 0.5) EnumFacing.DOWN else EnumFacing.UP
                    else -> if (hitZ < 0.5) EnumFacing.NORTH else EnumFacing.SOUTH
                }
                if (side == tileEntity.facing) {
                    tileEntity.shot(entity)
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    override fun getValidRotations(world: World, pos: BlockPos): Array<EnumFacing> {
        val tileEntity = world.getTileEntity(pos)
        return if (tileEntity is tileentity.Screen) {
            if (tileEntity.facing == EnumFacing.UP || tileEntity.facing == EnumFacing.DOWN) {
                EnumFacing.values()
            } else {
                EnumFacing.values().filter { d -> d != tileEntity.facing && d != tileEntity.facing.opposite }.toTypedArray()
            }
        } else super.getValidRotations(world, pos)
    }

    val emptyBB = AxisAlignedBB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    @SideOnly(Side.CLIENT)
    override fun getSelectedBoundingBox(state: IBlockState, worldIn: World, pos: BlockPos): AxisAlignedBB =
        if (!Minecraft.getMinecraft().player.isSneaking) emptyBB
        else super.getSelectedBoundingBox(state, worldIn, pos)
}
