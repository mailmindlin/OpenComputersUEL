package li.cil.oc.common.block

import li.cil.oc.common.GuiType
import li.cil.oc.common.block.traits.GUI
import li.cil.oc.integration.util.Wrench
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import li.cil.oc.common.tileentity.Adapter as TEAdapter

class Adapter : SimpleBlock(), GUI {
    override val guiType = GuiType.Adapter

    override fun createNewTileEntity(world: World, metadata: Int) = TEAdapter()

    // ----------------------------------------------------------------------- //

    override fun neighborChanged(state: IBlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos) {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TEAdapter) {
            tileEntity.neighborChanged()
        }
    }

    override fun onNeighborChange(world: IBlockAccess, pos: BlockPos, neighbor: BlockPos) {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is TEAdapter) {
            // TODO can we just pass the blockpos?
            val side = when {
                neighbor == pos.down() -> EnumFacing.DOWN
                neighbor == pos.up() -> EnumFacing.UP
                neighbor == pos.north() -> EnumFacing.NORTH
                neighbor == pos.south() -> EnumFacing.SOUTH
                neighbor == pos.west() -> EnumFacing.WEST
                neighbor == pos.east() -> EnumFacing.EAST
                else -> throw IllegalArgumentException("not a neighbor") // TODO wat
            }
            tileEntity.neighborChanged(side)
        }
    }

    override fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        if (Wrench.holdsApplicableWrench(player, pos)) {
            val sideToToggle = if (player.isSneaking) side.opposite else side
            val tileEntity = world.getTileEntity(pos)
            if (tileEntity is TEAdapter) {
                if (!world.isRemote) {
                    val oldValue = tileEntity.openSides(sideToToggle.ordinal)
                    tileEntity.setSideOpen(sideToToggle, !oldValue)
                }
                return true
            }
            return false
        }
        return super.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)
    }
}
