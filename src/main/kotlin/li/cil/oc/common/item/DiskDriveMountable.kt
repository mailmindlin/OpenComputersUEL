package li.cil.oc.common.item

import li.cil.oc.OpenComputers
import li.cil.oc.common.GuiType
import li.cil.oc.common.item.traits.Delegate
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.world.World

class DiskDriveMountable(parent: Delegator) : AbstractDelegate(parent) {
    override val maxStackSize: Int = 1

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        // Open the GUI immediately on the client, too, to avoid the player
        // changing the current slot before it actually opens, which can lead to
        // desynchronization of the player inventory.
        player.openGui(OpenComputers, GuiType.DiskDriveMountable.id, world, 0, 0, 0)
        player.swingArm(EnumHand.MAIN_HAND)
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack)
    }
}
