package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.block.traits.GUI
import li.cil.oc.common.tileentity.Disassembler as TEDisassembler
import li.cil.oc.util.Tooltip
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import li.cil.oc.common.block.traits.PowerAcceptor as TraitPowerAcceptor
import li.cil.oc.common.block.traits.StateAware as TraitStateAware
import li.cil.oc.common.block.traits.GUI as TraitGUI

class Disassembler : SimpleBlock(), TraitPowerAcceptor, TraitStateAware, TraitGUI {
    override fun tooltipBody(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, advanced: ITooltipFlag) {
        tooltip.addAll(Tooltip.get(javaClass.simpleName.lowercase(), (Settings.get.disassemblerBreakChance * 100).toInt().toString()))
    }

    // ----------------------------------------------------------------------- //

    override val energyThroughput: Double get() = Settings.get.disassemblerRate

    override val guiType = GuiType.Disassembler

    override fun createNewTileEntity(world: World, metadata: Int) = TEDisassembler()

    override fun localOnBlockActivated(
        world: World, pos: BlockPos,
        player: EntityPlayer, hand: EnumHand, heldItem: ItemStack,
        side: EnumFacing,
        hitX: Float, hitY: Float, hitZ: Float)
            : Boolean = super<TraitGUI>.localOnBlockActivated(world, pos, player, hand, heldItem, side, hitX, hitY, hitZ)
}
