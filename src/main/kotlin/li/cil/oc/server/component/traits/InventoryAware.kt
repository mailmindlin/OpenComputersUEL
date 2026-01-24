package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import li.cil.oc.util.checkSlot
import li.cil.oc.util.notEmpty
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack

interface InventoryAware: HasFakePlayer {
    val inventory: IInventory
    /** The selected slot */
    var selectedSlot: Int

    val insertionSlots: List<Int>
        get() = ((selectedSlot until inventory.sizeInventory) + (0 until selectedSlot)).toList()

    // ----------------------------------------------------------------------- //

    /**
     * Treat parameter `n` as an optional slot, otherwise defaulting to selected slot
     */
    fun Arguments.optSlot(n: Int): Int {
        return if (this.count() > 0 && this.checkAny(0) != null) {
            this.checkSlot(inventory, 0)
        } else {
            selectedSlot
        }
    }

    /**
     * Treat parameter `n` as a slot
     */
    fun Arguments.checkSlot(n: Int): Int =this.checkSlot(inventory, n)

    /** Gets the ItemStack in the `slot`, not empty */
    fun stackInSlot(slot: Int): ItemStack?
        = inventory.getStackInSlot(slot).notEmpty()
}
