package li.cil.oc.common.block

import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import li.cil.oc.util.Tooltip
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.world.World

class Disassembler : SimpleBlock(), traits.PowerAcceptor, traits.StateAware, traits.GUI {
    override fun tooltipBody(metadata: Int, stack: ItemStack, world: World, tooltip: java.util.List<String>, advanced: ITooltipFlag) {
        tooltip.addAll(Tooltip.get(javaClass.simpleName.lowercase(), (Settings.get.disassemblerBreakChance * 100).toInt().toString()))
    }

    // ----------------------------------------------------------------------- //

    override val energyThroughput: Double get() = Settings.get.disassemblerRate

    override val guiType = GuiType.Disassembler

    override fun createNewTileEntity(world: World, metadata: Int) = tileentity.Disassembler()
}
