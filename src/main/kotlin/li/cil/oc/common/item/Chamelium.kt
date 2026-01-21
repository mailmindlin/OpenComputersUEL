package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.traits.Delegate
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.EnumAction
import net.minecraft.item.ItemStack
import net.minecraft.potion.Potion
import net.minecraft.potion.PotionEffect
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.world.World

class Chamelium(parent: Delegator) : AbstractDelegate(parent) {
    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        if (Settings.get.chameliumEdible) {
            player.setActiveHand(if (player.heldItemMainhand == stack) EnumHand.MAIN_HAND else EnumHand.OFF_HAND)
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack)
    }

    override fun getItemUseAction(stack: ItemStack): EnumAction = EnumAction.EAT

    override fun getMaxItemUseDuration(stack: ItemStack): Int = 32

    override fun onItemUseFinish(stack: ItemStack, world: World, player: EntityLivingBase): ItemStack {
        if (!world.isRemote) {
            player.addPotionEffect(PotionEffect(Potion.getPotionFromResourceLocation("invisibility"), 100, 0))
            player.addPotionEffect(PotionEffect(Potion.getPotionFromResourceLocation("blindness"), 200, 0))
        }
        stack.shrink(1)
        return if (stack.count > 0) stack else ItemStack.EMPTY
    }
}
