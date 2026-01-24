package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity.Disassembler as TEDisassembler
import li.cil.oc.util.Tooltip
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
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
}
