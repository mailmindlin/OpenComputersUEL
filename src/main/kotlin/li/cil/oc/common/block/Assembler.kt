package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity.Assembler as TEAssembler
import net.minecraft.block.state.IBlockState
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import li.cil.oc.common.block.traits.PowerAcceptor as TraitPowerAcceptor
import li.cil.oc.common.block.traits.StateAware as TraitStateAware
import li.cil.oc.common.block.traits.GUI as TraitGUI

class Assembler : SimpleBlock(), TraitPowerAcceptor, TraitStateAware, TraitGUI {
    override fun isOpaqueCube(state: IBlockState): Boolean = false

    override fun isFullCube(state: IBlockState): Boolean = false

    override fun isBlockSolid(world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean =
        side == EnumFacing.DOWN || side == EnumFacing.UP

    override fun isSideSolid(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing): Boolean =
        side == EnumFacing.DOWN || side == EnumFacing.UP

    // ----------------------------------------------------------------------- //

    override val energyThroughput: Double get() = Settings.get.assemblerRate

    override val guiType = GuiType.Assembler

    override fun createNewTileEntity(world: World, metadata: Int) = TEAssembler()
}
