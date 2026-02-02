package li.cil.oc.common.block

import li.cil.oc.common.GuiType
import li.cil.oc.common.block.traits.GUI
import li.cil.oc.common.block.traits.StateAware
import li.cil.oc.common.tileentity.Printer as TEPrinter
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World

class Printer : SimpleBlock(), StateAware, GUI {
    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun isBlockSolid(world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = side == EnumFacing.DOWN

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = side == EnumFacing.DOWN

    // ----------------------------------------------------------------------- //

    override val guiType = GuiType.Printer

    override fun createNewTileEntity(world: World, metadata: Int) = TEPrinter()

    override fun localOnBlockActivated(
        world: World, pos: BlockPos,
        player: EntityPlayer, hand: EnumHand, heldItem: ItemStack,
        side: EnumFacing,
        hitX: Float, hitY: Float, hitZ: Float)
            : Boolean = super<GUI>.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)
}
