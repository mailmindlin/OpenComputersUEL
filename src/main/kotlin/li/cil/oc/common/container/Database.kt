package li.cil.oc.common.container

import li.cil.oc.common.inventory.DatabaseInventory
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import kotlin.math.ceil
import kotlin.math.sqrt

class Database(playerInventory: InventoryPlayer, val databaseInventory: DatabaseInventory) : Player(playerInventory, databaseInventory) {
    val rows: Int = ceil(sqrt(databaseInventory.sizeInventory.toDouble())).toInt()
    val offset: Int = 8 + arrayOf(3, 2, 0)[databaseInventory.tier] * slotSize

    init {
        for (row in 0 until rows) {
            for (col in 0 until rows) {
                addSlotToContainer(offset + col * slotSize, offset + row * slotSize)
            }
        }

        // Show the player's inventory.
        addPlayerInventorySlots(8, 174)
    }

    override fun canInteractWith(player: EntityPlayer): Boolean = player == playerInventory.player

    override fun slotClick(slot: Int, dragType: Int, clickType: ClickType, player: EntityPlayer): ItemStack {
        if (slot >= databaseInventory.sizeInventory || slot < 0) {
            // if the slot interaction is with the user inventory use
            // default behavior
            return super.slotClick(slot, dragType, clickType, player)
        }
        // remove the ghost item
        val ghostSlot = this.inventorySlots[slot]
        if (ghostSlot != null) {
            val inventoryPlayer = player.inventory
            val hand = inventoryPlayer.itemStack
            var itemToAdd = ItemStack.EMPTY
            // if the player is holding an item, place a copy
            if (!hand.isEmpty) {
                itemToAdd = hand.copy()
            }
            ghostSlot.putStack(itemToAdd)
        }
        return ItemStack.EMPTY
    }

    override fun tryTransferStackInSlot(from: Slot, intoPlayerInventory: Boolean) {
        if (intoPlayerInventory) {
            from.onSlotChanged()
            return
        }

        val fromStack = from.stack.copy()
        if (fromStack.isEmpty) {
            return
        }

        fromStack.count = 1
        val begin = 0
        val end = inventorySlots.size - 1

        for (i in begin..end) {
            val intoSlot = inventorySlots[i]
            if (intoSlot.inventory != from.inventory) {
                if (!intoSlot.hasStack && intoSlot.isItemValid(fromStack)) {
                    if (intoSlot.slotStackLimit > 0) {
                        intoSlot.putStack(fromStack)
                        return
                    }
                }
            }
        }
    }
}
