package li.cil.oc.common.item

import li.cil.oc.OpenComputers
import li.cil.oc.client.KeyBindings
import li.cil.oc.common.GuiType
import li.cil.oc.common.inventory.ServerInventory
import li.cil.oc.util.Rarity
import li.cil.oc.util.Tooltip
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.EnumRarity
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.world.World

class Server(parent: Delegator, tier: Int) : AbstractTieredDelegate(parent, tier) {
    override fun rarity(stack: ItemStack): EnumRarity = Rarity.byTier(tier)

    override val maxStackSize: Int = 1

    private object HelperInventory : ServerInventory() {
        override var container: ItemStack = ItemStack.EMPTY
    }

    override fun tooltipExtended(stack: ItemStack, tooltip: MutableList<String>) {
        super.tooltipExtended(stack, tooltip)
        if (KeyBindings.showExtendedTooltips) {
            HelperInventory.container = stack
            HelperInventory.reinitialize()
            val stacks = mutableMapOf<String, Int>()
            for (i in 0 until HelperInventory.sizeInventory) {
                val aStack = HelperInventory.getStackInSlot(i)
                if (!aStack.isEmpty) {
                    val displayName = aStack.displayName
                    stacks[displayName] = stacks.getOrDefault(displayName, 0) + 1
                }
            }
            if (stacks.isNotEmpty()) {
                tooltip.addAll(Tooltip.get("server.Components"))
                for (itemName in stacks.keys.sorted()) {
                    tooltip.add("- ${stacks[itemName]}x $itemName")
                }
            }
        }
    }

    override fun onItemRightClick(stack: ItemStack, world: World, player: EntityPlayer): ActionResult<ItemStack> {
        if (!player.isSneaking) {
            // Open the GUI immediately on the client, too, to avoid the player
            // changing the current slot before it actually opens, which can lead to
            // desynchronization of the player inventory.
            player.openGui(OpenComputers, GuiType.Server.id, world, 0, 0, 0)
            player.swingArm(EnumHand.MAIN_HAND)
        }
        return ActionResult.newResult(EnumActionResult.SUCCESS, stack)
    }
}
