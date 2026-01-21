package li.cil.oc.common.container

import li.cil.oc.common.Slot
import li.cil.oc.common.Tier
import li.cil.oc.common.tileentity.Raid as TERaid
import net.minecraft.entity.player.InventoryPlayer

class Raid(playerInventory: InventoryPlayer, raid: TERaid) : Player(playerInventory, raid) {
    init {
        addSlotToContainer(60, 23, Slot.HDD, Tier.Three)
        addSlotToContainer(80, 23, Slot.HDD, Tier.Three)
        addSlotToContainer(100, 23, Slot.HDD, Tier.Three)
        addPlayerInventorySlots(8, 84)
    }
}
