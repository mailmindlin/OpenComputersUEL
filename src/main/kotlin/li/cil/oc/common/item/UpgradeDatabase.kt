package li.cil.oc.common.item

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.common.GuiType
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier
import li.cil.oc.util.Rarity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.world.World

class UpgradeDatabase(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier), ItemTier {
    override val tooltipData: Array<Any> get() = arrayOf(Settings.get.databaseEntriesPerTier[tier])

    override fun rarity(stack: ItemStack) = Rarity.byTier(tier)

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        if (!player.isSneaking) {
            player.openGui(OpenComputers, GuiType.Database.id, world, 0, 0, 0)
            player.swingArm(EnumHand.MAIN_HAND)
        } else if (stack.hasTagCompound() && stack.tagCompound!!.hasKey(Settings.namespace + "items")) {
            stack.tagCompound = null
            player.swingArm(EnumHand.MAIN_HAND)
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack)
    }
}
