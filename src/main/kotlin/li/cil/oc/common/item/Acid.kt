package li.cil.oc.common.item

import li.cil.oc.api.Nanomachines as ApiNanomachines
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

class Acid(override val parent: Delegator) : Delegate {
    override var showInItemList: Boolean = false
    override val itemId: Int = 0

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        player.setActiveHand(if (player.heldItemMainhand == stack) EnumHand.MAIN_HAND else EnumHand.OFF_HAND)
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack)
    }

    override fun getItemUseAction(stack: ItemStack): EnumAction = EnumAction.DRINK

    override fun getMaxItemUseDuration(stack: ItemStack): Int = 32

    override fun onItemUseFinish(stack: ItemStack, world: World, entity: EntityLivingBase): ItemStack {
        if (entity is EntityPlayer) {
            if (!world.isRemote) {
                entity.addPotionEffect(PotionEffect(Potion.getPotionFromResourceLocation("blindness"), 200))
                entity.addPotionEffect(PotionEffect(Potion.getPotionFromResourceLocation("poison"), 100))
                entity.addPotionEffect(PotionEffect(Potion.getPotionFromResourceLocation("slowness"), 600))
                entity.addPotionEffect(PotionEffect(Potion.getPotionFromResourceLocation("nausea"), 1200))
                entity.addPotionEffect(PotionEffect(Potion.getPotionFromResourceLocation("saturation"), 2000))

                // Remove nanomachines if installed.
                ApiNanomachines.uninstallController(entity)
            }
            stack.shrink(1)
            return if (stack.count > 0) stack else ItemStack.EMPTY
        }
        return stack
    }
}
