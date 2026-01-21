package li.cil.oc.common.item.traits

import li.cil.oc.Localization
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

interface ItemTier : Delegate {
    @SideOnly(Side.CLIENT)
    override fun tooltipLines(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        super.tooltipLines(stack, world, tooltip, flag)
        if (flag.isAdvanced) {
            tooltip.add(Localization.Tooltip.Tier(tierFromDriver(stack) + 1))
        }
    }
}
