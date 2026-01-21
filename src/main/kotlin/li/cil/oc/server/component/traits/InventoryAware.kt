package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import li.cil.oc.util.ExtendedArguments.checkSlot
import li.cil.oc.util.StackOption
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory

interface InventoryAware {
    val fakePlayer: EntityPlayer
    val inventory: IInventory
    var selectedSlot: Int

    val insertionSlots: List<Int>
        get() = ((selectedSlot until inventory.sizeInventory) + (0 until selectedSlot)).toList()

    // ----------------------------------------------------------------------- //

    fun optSlot(args: Arguments, n: Int): Int {
        return if (args.count() > 0 && args.checkAny(0) != null) {
            args.checkSlot(inventory, 0)
        } else {
            selectedSlot
        }
    }

    fun stackInSlot(slot: Int): StackOption {
        return StackOption(inventory.getStackInSlot(slot))
    }
}
