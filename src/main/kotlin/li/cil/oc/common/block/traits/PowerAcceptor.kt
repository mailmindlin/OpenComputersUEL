package li.cil.oc.common.block.traits

import li.cil.oc.util.Tooltip
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.world.World

interface PowerAcceptor {
    val energyThroughput: Double

    // ----------------------------------------------------------------------- //

    fun powerAcceptorTooltipTail(metadata: Int, stack: ItemStack, world: World, tooltip: java.util.List<String>, advanced: ITooltipFlag) {
        tooltip.addAll(Tooltip.extended("poweracceptor", energyThroughput.toInt()))
    }
}
