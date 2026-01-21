package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.FileSystemLike
import li.cil.oc.common.item.traits.ItemTier
import net.minecraft.item.ItemStack

class HardDiskDrive(override val parent: Delegator, val tier: Int) : Delegate, ItemTier, FileSystemLike {
    override val unlocalizedName: String = super.unlocalizedName + tier
    override val kiloBytes: Int = Settings.get.hddSizes(tier)
    val platterCount: Int = Settings.get.hddPlatterCounts(tier)

    override fun displayName(stack: ItemStack): String {
        val localizedName = parent.internalGetItemStackDisplayName(stack)
        return if (kiloBytes >= 1024) {
            "$localizedName (${kiloBytes / 1024}MB)"
        } else {
            "$localizedName (${kiloBytes}KB)"
        }
    }
}
