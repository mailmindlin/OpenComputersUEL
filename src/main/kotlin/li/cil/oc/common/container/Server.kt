package li.cil.oc.common.container

import li.cil.oc.common.InventorySlots
import li.cil.oc.common.inventory.ServerInventory
import li.cil.oc.server.component.Server as ServerComponent
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.nbt.NBTTagCompound

class Server @JvmOverloads constructor(
    playerInventory: InventoryPlayer,
    serverInventory: ServerInventory,
    val server: ServerComponent? = null
) : Player<ServerInventory>(playerInventory, serverInventory) {

    var isRunning: Boolean = false
    var isItem: Boolean = true

    init {
        for (i in 0..1) {
            val slot = InventorySlots.server[serverInventory.tier][inventory.size]
            addSlotToContainer(76, 7 + i * slotSize, slot.slot, slot.tier)
        }

        val verticalSlots = minOf(3, 1 + serverInventory.tier)
        for (i in 0..verticalSlots) {
            val slot = InventorySlots.server[serverInventory.tier][inventory.size]
            addSlotToContainer(100, 7 + i * slotSize, slot.slot, slot.tier)
        }

        for (i in 0..verticalSlots) {
            val slot = InventorySlots.server[serverInventory.tier][inventory.size]
            addSlotToContainer(124, 7 + i * slotSize, slot.slot, slot.tier)
        }

        for (i in 0..verticalSlots) {
            val slot = InventorySlots.server[serverInventory.tier][inventory.size]
            addSlotToContainer(148, 7 + i * slotSize, slot.slot, slot.tier)
        }

        for (i in 2..verticalSlots) {
            val slot = InventorySlots.server[serverInventory.tier][inventory.size]
            addSlotToContainer(76, 7 + i * slotSize, slot.slot, slot.tier)
        }

        run {
            val slot = InventorySlots.server[serverInventory.tier][inventory.size]
            addSlotToContainer(26, 34, slot.slot, slot.tier)
        }

        // Show the player's inventory.
        addPlayerInventorySlots(8, 84)
    }

    override fun canInteractWith(player: EntityPlayer): Boolean {
        return if (server != null) super.canInteractWith(player)
        else player == playerInventory.player
    }

    override fun updateCustomData(nbt: NBTTagCompound) {
        super.updateCustomData(nbt)
        isRunning = nbt.getBoolean("isRunning")
        isItem = nbt.getBoolean("isItem")
    }

    override fun detectCustomDataChanges(nbt: NBTTagCompound) {
        super.detectCustomDataChanges(nbt)
        if (server != null) {
            nbt.setBoolean("isRunning", server.machine.isRunning)
        } else {
            nbt.setBoolean("isItem", true)
        }
    }
}
