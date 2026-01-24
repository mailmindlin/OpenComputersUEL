package li.cil.oc.common.container

import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity.Adapter as TEAdapter
import net.minecraft.entity.player.InventoryPlayer

class Adapter(playerInventory: InventoryPlayer, adapter: TEAdapter) : Player<TEAdapter>(playerInventory, adapter) {
    init {
        addSlotToContainer(80, 35, Slot.Upgrade)
        addPlayerInventorySlots(8, 84)
    }
}
