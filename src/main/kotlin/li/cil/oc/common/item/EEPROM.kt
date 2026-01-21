package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess

class EEPROM(override val parent: Delegator) : Delegate {
    override fun displayName(stack: ItemStack): String? {
        if (stack.hasTagCompound()) {
            val tag = stack.tagCompound!!
            if (tag.hasKey(Settings.namespace + "data")) {
                val data = tag.getCompoundTag(Settings.namespace + "data")
                if (data.hasKey(Settings.namespace + "label")) {
                    return data.getString(Settings.namespace + "label")
                }
            }
        }
        return super.displayName(stack)
    }

    override fun doesSneakBypassUse(world: IBlockAccess, pos: BlockPos, player: EntityPlayer): Boolean = true
}
