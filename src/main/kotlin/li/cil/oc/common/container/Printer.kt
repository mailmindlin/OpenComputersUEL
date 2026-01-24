package li.cil.oc.common.container

import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity.Printer as TEPrinter
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.nbt.NBTTagCompound

class Printer(playerInventory: InventoryPlayer, val printer: TEPrinter) : Player<TEPrinter>(playerInventory, printer) {
    init {
        addSlotToContainer(18, 19, Slot.Filtered)
        addSlotToContainer(18, 51, Slot.Filtered)
        addSlotToContainer(152, 35)

        // Show the player's inventory.
        addPlayerInventorySlots(8, 84)
    }

    fun progress(): Double = synchronizedData.getDouble("progress")

    fun amountMaterial(): Int = synchronizedData.getInteger("amountMaterial")

    fun amountInk(): Int = synchronizedData.getInteger("amountInk")

    override fun detectCustomDataChanges(nbt: NBTTagCompound) {
        synchronizedData.setDouble("progress", if (printer.isPrinting) printer.progress / 100.0 else 0.0)
        synchronizedData.setInteger("amountMaterial", printer.amountMaterial)
        synchronizedData.setInteger("amountInk", printer.amountInk)
        super.detectCustomDataChanges(nbt)
    }
}
