package li.cil.oc.common.item

import li.cil.oc.OpenComputers
import li.cil.oc.api.Manual as ApiManual
import li.cil.oc.util.BlockPosition
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.text.TextFormatting
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class Manual(parent: Delegator) : AbstractDelegate(parent) {
    @SideOnly(Side.CLIENT)
    override fun tooltipLines(stack: ItemStack, world: World?, tooltip: MutableList<String>, flag: ITooltipFlag) {
        tooltip.add(TextFormatting.DARK_GRAY.toString() + "v" + OpenComputers.Version)
        super.tooltipLines(stack, world, tooltip, flag)
    }

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        if (world.isRemote) {
            if (player.isSneaking) {
                ApiManual.reset()
            }
            ApiManual.openFor(player)
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack)
    }

    override fun onItemUse(stack: ItemStack, player: EntityPlayer, position: BlockPosition, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        val world = player.entityWorld
        val path = ApiManual.pathFor(world, position.toBlockPos()) ?: return super.onItemUse(stack, player, position, side, hitX, hitY, hitZ)
        if (world.isRemote) {
            ApiManual.openFor(player)
            ApiManual.reset()
            ApiManual.navigate(path)
        }
        return true
    }
}
