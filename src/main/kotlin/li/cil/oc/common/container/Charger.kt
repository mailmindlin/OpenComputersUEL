package li.cil.oc.common.container

import li.cil.oc.common.tileentity
import net.minecraft.entity.player.InventoryPlayer

class Charger(playerInventory: InventoryPlayer, charger: tileentity.Charger) : Player(playerInventory, charger) {
    init {
        addSlotToContainer(80, 35, "tablet")
        addPlayerInventorySlots(8, 84)
    }
}
