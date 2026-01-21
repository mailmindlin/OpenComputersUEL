package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.tileentity
import li.cil.oc.util.Rarity
import li.cil.oc.util.Tooltip
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class Case(val tier: Int) : RedstoneAware(), traits.PowerAcceptor, traits.StateAware, traits.GUI {
    override fun createBlockState(): BlockStateContainer = BlockStateContainer(this, PropertyRotatable.Facing, property.PropertyRunning.Running)

    override fun getStateFromMeta(meta: Int): IBlockState = defaultState.withProperty(PropertyRotatable.Facing, EnumFacing.byHorizontalIndex(meta shr 1))

    override fun getMetaFromState(state: IBlockState): Int = (state.getValue(PropertyRotatable.Facing).horizontalIndex shl 1) or (if (state.getValue(property.PropertyRunning.Running)) 1 else 0)

    // ----------------------------------------------------------------------- //

    override fun rarity(stack: ItemStack) = Rarity.byTier(tier)

    override fun tooltipBody(metadata: Int, stack: ItemStack, world: World, tooltip: java.util.List<String>, advanced: ITooltipFlag) {
        tooltip.addAll(Tooltip.get(javaClass.simpleName.lowercase(), slots))
    }

    private val slots: String
        get() = when (tier) {
            0 -> "2/1/1"
            1 -> "2/2/2"
            2, 3 -> "3/2/3"
            else -> "0/0/0"
        }

    // ----------------------------------------------------------------------- //

    override val energyThroughput: Double get() = Settings.get.caseRate(tier)

    override val guiType = GuiType.Case

    override fun createNewTileEntity(world: World, metadata: Int) = tileentity.Case(tier)

    // ----------------------------------------------------------------------- //

    override fun localOnBlockActivated(world: World, pos: BlockPos, player: EntityPlayer, hand: EnumHand, heldItem: ItemStack, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        if (player.isSneaking) {
            if (!world.isRemote) {
                val tileEntity = world.getTileEntity(pos)
                if (tileEntity is tileentity.Case && !tileEntity.machine.isRunning && tileEntity.isUsableByPlayer(player)) {
                    tileEntity.machine.start()
                }
            }
            return true
        }
        return super.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)
    }

    override fun removedByPlayer(state: IBlockState, world: World, pos: BlockPos, player: EntityPlayer, willHarvest: Boolean): Boolean {
        val tileEntity = world.getTileEntity(pos)
        return when {
            tileEntity is tileentity.Case -> {
                if (tileEntity.isCreative && (!player.capabilities.isCreativeMode || !tileEntity.canInteract(player.name))) false
                else tileEntity.canInteract(player.name) && super.removedByPlayer(state, world, pos, player, willHarvest)
            }
            else -> super.removedByPlayer(state, world, pos, player, willHarvest)
        }
    }
}
