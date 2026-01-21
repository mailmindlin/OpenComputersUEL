package li.cil.oc.common.item

import li.cil.oc.Localization
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier
import li.cil.oc.integration.opencomputers.Item as OcItem
import li.cil.oc.util.UpgradeExperience as UpgradeExperienceUtil
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class UpgradeExperience(override val parent: Delegator) : Delegate, ItemTier {
    @SideOnly(Side.CLIENT)
    override fun tooltipLines(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        if (stack.hasTagCompound()) {
            val nbt = OcItem.dataTag(stack)
            val experience = UpgradeExperienceUtil.getExperience(nbt)
            val level = UpgradeExperienceUtil.calculateLevelFromExperience(experience)
            val reportedLevel = UpgradeExperienceUtil.calculateExperienceLevel(level, experience)
            tooltip.add(Localization.Tooltip.ExperienceLevel(reportedLevel))
        }
        super.tooltipLines(stack, world, tooltip, flag)
    }
}
