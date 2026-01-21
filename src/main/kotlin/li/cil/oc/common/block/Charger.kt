package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.tileentity
import li.cil.oc.integration.util.Wrench
import li.cil.oc.server.PacketSender
import net.minecraft.block.Block
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World

class Charger : RedstoneAware(), traits.PowerAcceptor, traits.StateAware, traits.GUI {
    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, PropertyRotatable.Facing)

    override fun getStateFromMeta(meta: Int): IBlockState = defaultState.withProperty(PropertyRotatable.Facing, EnumFacing.byHorizontalIndex(meta))

    override fun getMetaFromState(state: IBlockState): Int = state.getValue(PropertyRotatable.Facing).horizontalIndex

    // ----------------------------------------------------------------------- //

    override val energyThroughput: Double get() = Settings.get.chargerRate

    override val guiType = GuiType.Charger

    override fun createNewTileEntity(world: World, metadata: Int) = tileentity.Charger()

    // ----------------------------------------------------------------------- //

    override fun canConnectRedstone(state: IBlockState, world: IBlockAccess, pos: BlockPos, side: EnumFacing?): Boolean = true

    // ----------------------------------------------------------------------- //

    override fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        if (Wrench.holdsApplicableWrench(player, pos)) {
            val tileEntity = world.getTileEntity(pos)
            if (tileEntity is tileentity.Charger) {
                if (!world.isRemote) {
                    tileEntity.invertSignal = !tileEntity.invertSignal
                    tileEntity.chargeSpeed = 1.0 - tileEntity.chargeSpeed
                    PacketSender.sendChargerState(tileEntity)
                    Wrench.wrenchUsed(player, pos)
                }
                return true
            }
            return false
        }
        return super.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)
    }

    override fun neighborChanged(state: IBlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos) {
        val tileEntity = world.getTileEntity(pos)
        if (tileEntity is tileentity.Charger) {
            tileEntity.onNeighborChanged()
        }
        super.neighborChanged(state, world, pos, block, fromPos)
    }
}
