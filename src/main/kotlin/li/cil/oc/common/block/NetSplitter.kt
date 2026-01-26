package li.cil.oc.common.block

import li.cil.oc.common.block.property.PropertyTile
import li.cil.oc.common.tileentity.NetSplitter as TENetSplitter
import li.cil.oc.integration.util.Wrench
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.property.ExtendedBlockState
import net.minecraftforge.common.property.IExtendedBlockState

class NetSplitter : RedstoneAware() {
    override fun createBlockState() = ExtendedBlockState(this, emptyArray(), arrayOf(PropertyTile))

    override fun getExtendedState(state: IBlockState, world: IBlockAccess, pos: BlockPos): IBlockState {
        val tileEntity = world.getTileEntity(pos)
        return if (state is IExtendedBlockState && tileEntity is TENetSplitter) {
            state.withProperty(PropertyTile, tileEntity)
        } else state
    }

    // ----------------------------------------------------------------------- //

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean = false

    // ----------------------------------------------------------------------- //

    override fun createNewTileEntity(world: World, meta: Int) = TENetSplitter()

    // ----------------------------------------------------------------------- //

    // NOTE: must not be final for immibis microblocks to work.
    override fun onBlockActivated(world: World, pos: BlockPos, state: IBlockState, player: EntityPlayer, hand: EnumHand, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        if (Wrench.holdsApplicableWrench(player, pos)) {
            val sideToToggle = if (player.isSneaking) side.opposite else side
            val tileEntity = world.getTileEntity(pos)
            if (tileEntity is TENetSplitter) {
                if (!world.isRemote) {
                    val oldValue = tileEntity.openSides[sideToToggle.ordinal]
                    tileEntity.setSideOpen(sideToToggle, !oldValue)
                }
                return true
            }
            return false
        }
        return super.onBlockActivated(world, pos, state, player, hand, side, hitX, hitY, hitZ)
    }
}
