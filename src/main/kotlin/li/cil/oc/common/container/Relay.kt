package li.cil.oc.common.container

import li.cil.oc.common.Slot
import li.cil.oc.common.tileentity.Relay as TERelay
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.nbt.NBTTagCompound

class Relay(playerInventory: InventoryPlayer, val relay: TERelay) : Player<TERelay>(playerInventory, relay) {
    init {
        addSlotToContainer(151, 15, Slot.CPU)
        addSlotToContainer(151, 34, Slot.Memory)
        addSlotToContainer(151, 53, Slot.HDD)
        addSlotToContainer(178, 15, Slot.Card)
        addPlayerInventorySlots(8, 84)
    }

    fun relayDelay(): Int = synchronizedData.getInteger("relayDelay")

    fun relayAmount(): Int = synchronizedData.getInteger("relayAmount")

    fun maxQueueSize(): Int = synchronizedData.getInteger("maxQueueSize")

    fun packetsPerCycleAvg(): Int = synchronizedData.getInteger("packetsPerCycleAvg")

    fun queueSize(): Int = synchronizedData.getInteger("queueSize")

    override fun detectCustomDataChanges(nbt: NBTTagCompound) {
        synchronizedData.setInteger("relayDelay", relay.relayDelay)
        synchronizedData.setInteger("relayAmount", relay.relayAmount)
        synchronizedData.setInteger("maxQueueSize", relay.maxQueueSize)
        synchronizedData.setInteger("packetsPerCycleAvg", relay.packetsPerCycleAvg())
        synchronizedData.setInteger("queueSize", relay.queue.size)
        super.detectCustomDataChanges(nbt)
    }
}
