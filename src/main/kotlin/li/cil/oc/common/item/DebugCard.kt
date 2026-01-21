package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.Settings.DebugCardAccess
import li.cil.oc.common.item.data.DebugCardData
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.server.component.DebugCard as CDebugCard
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.util.text.TextComponentString
import net.minecraft.world.World

class DebugCard(parent: Delegator) : AbstractDelegate(parent) {
    override fun tooltipExtended(stack: ItemStack, tooltip: MutableList<String>) {
        super.tooltipExtended(stack, tooltip)
        val data = DebugCardData(stack)
        data.access?.let { access -> tooltip.add("\u00a78${access.player}\u00a7r") }
    }

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        if (!world.isRemote && player.isSneaking) {
            val data = DebugCardData(stack)
            val name = player.name

            if (data.access?.player == name) {
                data.access = null
            } else {
                val access = Settings.get.debugCardAccess
                if (access is DebugCardAccess.Whitelist) {
                    val nonce = access.nonce(name)
                    if (nonce == null) {
                        player.sendMessage(TextComponentString("\u00a7cYou are not whitelisted to use debug card"))
                        player.swingArm(EnumHand.MAIN_HAND)
                        return ActionResult(EnumActionResult.FAIL, stack)
                    }
                    data.access = CDebugCard.AccessContext(name, nonce)
                } else {
                    data.access = CDebugCard.AccessContext(name, "")
                }
            }

            data.save(stack)
            player.swingArm(EnumHand.MAIN_HAND)
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack)
    }
}
