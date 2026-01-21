package li.cil.oc.common.container

import li.cil.oc.client.Textures
import li.cil.oc.common
import li.cil.oc.common.InventorySlots.InventorySlot
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.SideTracker
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

class DynamicComponentSlot(
    override val container: Player,
    inventory: IInventory,
    index: Int,
    x: Int,
    y: Int,
    val info: (DynamicComponentSlot) -> InventorySlot,
    val containerTierGetter: () -> Int
) : ComponentSlot(inventory, index, x, y) {

    override val tier: Int
        get() {
            val mainTier = containerTierGetter()
            return if (mainTier >= 0) info(this).tier
            else mainTier
        }

    override val tierIcon: ResourceLocation?
        get() = Textures.Icons.get(tier)

    override val slot: String
        get() {
            val mainTier = containerTierGetter()
            return if (mainTier >= 0) info(this).slot
            else common.Slot.None
        }

    override fun hasBackground(): Boolean = Textures.Icons.get(slot) != null

    override fun getBackgroundLocation(): ResourceLocation? = Textures.Icons.get(slot) ?: super.getBackgroundLocation()

    override fun getSlotStackLimit(): Int = when (slot) {
        common.Slot.Tool, common.Slot.Any, common.Slot.Filtered -> super.getSlotStackLimit()
        common.Slot.None -> 0
        else -> 1
    }

    override fun clearIfInvalid(player: EntityPlayer) {
        if (SideTracker.isServer && hasStack && !isItemValid(stack)) {
            val stack = this.stack
            putStack(ItemStack.EMPTY)
            InventoryUtils.addToPlayerInventory(stack, player)
        }
    }
}
