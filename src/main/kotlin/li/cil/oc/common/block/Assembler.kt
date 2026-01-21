package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import net.minecraft.block.state.IBlockState
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World

class Assembler : SimpleBlock(), traits.PowerAcceptor, traits.StateAware, traits.GUI {
    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun isBlockSolid(world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean =
        side == EnumFacing.DOWN || side == EnumFacing.UP

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean =
        side == EnumFacing.DOWN || side == EnumFacing.UP

    // ----------------------------------------------------------------------- //

    override val energyThroughput: Double get() = Settings.get.assemblerRate

    override val guiType = GuiType.Assembler

    override fun createNewTileEntity(world: World, metadata: Int) = tileentity.Assembler()
}
