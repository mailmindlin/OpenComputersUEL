package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.FileSystemLike
import li.cil.oc.common.item.traits.ItemTier
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.world.World

class HardDiskDrive(parent: Delegator, internal val tier: Int) : AbstractDelegate(parent), ItemTier, FileSystemLike {
    override fun tooltipLines(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        super<ItemTier>.tooltipLines(stack, world, tooltip, flag)
        super<FileSystemLike>.tooltipLines(stack, world, tooltip, flag)
    }

    override val unlocalizedName: String = super<AbstractDelegate>.unlocalizedName + tier
    override val kiloBytes: Int = Settings.get.hddSizes[tier]

    val platterCount: Int = Settings.get.hddPlatterCounts[tier]

    override fun displayName(stack: ItemStack): String {
        val localizedName = parent.internalGetItemStackDisplayName(stack)
        return if (kiloBytes >= 1024) {
            "$localizedName (${kiloBytes / 1024}MB)"
        } else {
            "$localizedName (${kiloBytes}KB)"
        }
    }
}
