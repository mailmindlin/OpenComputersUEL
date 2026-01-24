package li.cil.oc.common.block

import li.cil.oc.common.tileentity.Redstone as TERedstone
import li.cil.oc.integration.Mods
import li.cil.oc.util.Tooltip
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.world.World

class Redstone : RedstoneAware() {
    override fun tooltipTail(metadata: Int, stack: ItemStack, world: World, tooltip: MutableList<String>, advanced: ITooltipFlag) {
        super.tooltipTail(metadata, stack, world, tooltip, advanced)
        // todo more generic way for redstone mods to provide lines
        if (Mods.ProjectRedTransmission.isModAvailable) {
            tooltip.addAll(Tooltip.get("RedstoneCard.ProjectRed"))
        }
        if (Mods.Charset.isModAvailable) {
            tooltip.addAll(Tooltip.get("RedstoneCard.Charset"))
        }
    }

    // ----------------------------------------------------------------------- //

    override fun createNewTileEntity(world: World, metadata: Int) = TERedstone()
}
