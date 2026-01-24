package li.cil.oc.common.container

import li.cil.oc.common.item.TabletWrapper
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.InventoryPlayer

class Tablet(playerInventory: InventoryPlayer, tablet: TabletWrapper) : Player<TabletWrapper>(playerInventory, tablet) {
    init {
        addSlotToContainer(StaticComponentSlot(this, otherInventory, otherInventory.sizeInventory - 1, 80, 35, tablet.containerSlotType, tablet.containerSlotTier))

        addPlayerInventorySlots(8, 84)
    }

    override fun canInteractWith(player: EntityPlayer): Boolean = player == playerInventory.player
}
