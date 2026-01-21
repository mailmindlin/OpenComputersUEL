package li.cil.oc.server.agent

import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.Container
import net.minecraft.inventory.IContainerListener
import net.minecraft.inventory.IInventory
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraft.util.NonNullList

class AgentContainer(private val player: Player) : Container() {
    init {
        for (slot in 0 until player.agent.equipmentInventory().sizeInventory) {
            this.addSlotToContainer(Slot(player.inventory, -1 - slot, 0, 0))
        }
        for (slot in 0 until player.agent.mainInventory().sizeInventory) {
            this.addSlotToContainer(Slot(player.inventory, slot, 0, 0))
        }

        this.addListener(object : IContainerListener {
            override fun sendAllContents(containerToSend: Container, itemsList: NonNullList<ItemStack>) {}

            override fun sendWindowProperty(containerIn: Container, varToUpdate: Int, newValue: Int) {}

            override fun sendAllWindowProperties(containerIn: Container, inventory: IInventory) {}

            override fun sendSlotContents(containerToSend: Container, index: Int, stack: ItemStack) {
                // an action has updated the agent.inventory via slots
                // thus the player.inventory is outdated in this regard
                val relativeIndex = containerToSend.inventorySlots[index].slotIndex

                if (relativeIndex < 0) {
                    if (relativeIndex.inv() < player.inventory.armorInventory.size) {
                        player.inventory.armorInventory[relativeIndex.inv()] = stack
                    }
                } else if (relativeIndex < player.inventory.mainInventory.size) {
                    player.inventory.mainInventory[relativeIndex] = stack
                }
            }
        })
    }

    override fun canInteractWith(player: EntityPlayer): Boolean = true
}
