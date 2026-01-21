package li.cil.oc.client.gui.traits

import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack

interface LockedHotbar {
    val lockedStack: ItemStack

    fun handleMouseClickLocked(
        slot: Slot?,
        slotId: Int,
        mouseButton: Int,
        clickType: ClickType,
        superHandler: (Slot?, Int, Int, ClickType) -> Unit
    ) {
        if (slot == null || !slot.stack.isItemEqual(lockedStack)) {
            superHandler(slot, slotId, mouseButton, clickType)
        }
    }

    fun checkHotbarKeysLocked(keyCode: Int): Boolean = false
}
