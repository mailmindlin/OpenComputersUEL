package li.cil.oc.common.item

import com.google.common.base.Strings
import li.cil.oc.common.item.data.NanomachineData
import li.cil.oc.common.nanomachines.ControllerImpl
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.EnumAction
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import li.cil.oc.api.Nanomachines as ApiNanomachines

class Nanomachines(parent: Delegator) : AbstractDelegate(parent) {
    override fun rarity(stack: ItemStack): EnumRarity = EnumRarity.UNCOMMON

    @SideOnly(Side.CLIENT)
    override fun tooltipLines(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        super.tooltipLines(stack, world, tooltip, flag)
        if (stack.hasTagCompound()) {
            val data = NanomachineData(stack)
            if (!Strings.isNullOrEmpty(data.uuid)) {
                tooltip.add("\u00a78${data.uuid.substring(0, 13)}...\u00a77")
            }
        }
    }

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        player.setActiveHand(if (player.heldItemMainhand == stack) EnumHand.MAIN_HAND else EnumHand.OFF_HAND)
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack)
    }

    override fun getItemUseAction(stack: ItemStack): EnumAction = EnumAction.EAT

    override fun getMaxItemUseDuration(stack: ItemStack): Int = 32

    override fun onItemUseFinish(stack: ItemStack, world: World, entity: EntityLivingBase): ItemStack {
        if (entity is EntityPlayer) {
            if (!world.isRemote) {
                val data = NanomachineData(stack)

                // Re-install to get new address, make sure we're configured.
                ApiNanomachines.uninstallController(entity)
                val controller = ApiNanomachines.installController(entity)!!
                if (controller is ControllerImpl) {
                    val configuration = data.configuration
                    if (configuration != null) {
                        if (!Strings.isNullOrEmpty(data.uuid)) {
                            controller.uuid = data.uuid
                        }
                        controller.configuration.load(configuration)
                    } else {
                        controller.reconfigure()
                    }
                } else {
                    controller.reconfigure() // Huh.
                }
            }
            stack.shrink(1)
            return if (stack.count > 0) stack else ItemStack.EMPTY
        }
        return stack
    }
}
