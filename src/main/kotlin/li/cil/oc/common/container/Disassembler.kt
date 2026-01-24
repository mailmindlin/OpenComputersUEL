package li.cil.oc.common.container

import li.cil.oc.common.tileentity.Disassembler as TEDisassembler
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.nbt.NBTTagCompound

class Disassembler(playerInventory: InventoryPlayer, val disassembler: TEDisassembler) : Player<TEDisassembler>(playerInventory, disassembler) {
    init {
        addSlotToContainer(80, 35, "ocitem")
        addPlayerInventorySlots(8, 84)
    }

    fun disassemblyProgress(): Double = synchronizedData.getDouble("disassemblyProgress")

    override fun detectCustomDataChanges(nbt: NBTTagCompound) {
        synchronizedData.setDouble("disassemblyProgress", disassembler.progress)
        super.detectCustomDataChanges(nbt)
    }
}
