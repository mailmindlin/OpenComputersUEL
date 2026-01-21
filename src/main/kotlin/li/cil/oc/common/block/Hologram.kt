package li.cil.oc.common.block

import li.cil.oc.common.tileentity.Hologram as TEHologram
import li.cil.oc.util.Rarity
import li.cil.oc.util.Tooltip
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class Hologram(val tier: Int) : SimpleBlock() {
    val bounds = AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 0.5, 1.0)

    // ----------------------------------------------------------------------- //

    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun isBlockSolid(world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = side == EnumFacing.DOWN

    @SideOnly(Side.CLIENT)
    override fun shouldSideBeRendered(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean {
        return super.shouldSideBeRendered(state, world, pos, side) || side == EnumFacing.UP
    }

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = side == EnumFacing.DOWN

    override fun getBoundingBox(state: IBlockState, world: IBlockAccess, pos: BlockPos): AxisAlignedBB = bounds

    // ----------------------------------------------------------------------- //

    override fun rarity(stack: ItemStack) = Rarity.byTier(tier)

    override fun tooltipBody(metadata: Int, stack: ItemStack, world: World, tooltip: java.util.List<String>, advanced: ITooltipFlag) {
        tooltip.addAll(Tooltip.get(javaClass.simpleName.lowercase() + tier))
    }

    // ----------------------------------------------------------------------- //

    override fun createNewTileEntity(world: World, metadata: Int) = TEHologram(tier)
}
