package li.cil.oc.common.container

import li.cil.oc.common.InventorySlots.InventorySlot
import li.cil.oc.common.Tier
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.util.SideTracker
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.inventory.ClickType
import net.minecraft.inventory.Container
import net.minecraft.inventory.IInventory
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.common.util.FakePlayer

abstract class Player<out I: IInventory>(val playerInventory: InventoryPlayer, val otherInventory: I) : Container() {
    /** Number of player inventory slots to display horizontally. */
    protected val playerInventorySizeX: Int = minOf(9, InventoryPlayer.getHotbarSize())

    /** Subtract four for armor slots. */
    protected val playerInventorySizeY: Int = minOf(4, (playerInventory.sizeInventory - 4) / playerInventorySizeX)

    /** Render size of slots (width and height). */
    protected val slotSize: Int = 18

    private var lastSync: Long = System.currentTimeMillis()

    override fun canInteractWith(player: EntityPlayer): Boolean = otherInventory.isUsableByPlayer(player)

    override fun slotClick(slot: Int, dragType: Int, clickType: ClickType, player: EntityPlayer): ItemStack {
        val result = super.slotClick(slot, dragType, clickType, player)
        if (SideTracker.isServer()) {
            detectAndSendChanges() // We have to enforce this more than MC does itself
            // because stacks can change their... "character" just by being inserted in
            // certain containers - by being assigned an address.
        }
        return result
    }

    override fun transferStackInSlot(player: EntityPlayer, index: Int): ItemStack {
        val slot = inventorySlots.getOrNull(index)
        if (slot != null && slot.hasStack) {
            tryTransferStackInSlot(slot, slot.inventory == otherInventory)
            if (SideTracker.isServer()) {
                detectAndSendChanges()
            }
        }
        return ItemStack.EMPTY
    }

    // return true if all items have been moved or no more work to do
    protected fun tryMoveAllSlotToSlot(from: Slot?, to: Slot?): Boolean {
        if (to == null)
            return false // nowhere to move it

        if (from == null ||
            !from.hasStack ||
            from.stack.isEmpty)
            return true // all moved because nothing to move

        if (to.inventory == from.inventory)
            return false // not intended for moving in the same inventory

        // for ghost slots we don't care about stack size
        val fromStack = from.stack
        val toStack = if (to.hasStack) to.stack else ItemStack.EMPTY
        val toStackSize = if (!toStack.isEmpty) toStack.count else 0

        val maxStackSize = minOf(fromStack.maxStackSize, to.slotStackLimit)
        val itemsMoved = minOf(maxStackSize - toStackSize, fromStack.count)

        if (!toStack.isEmpty) {
            if (toStackSize < maxStackSize &&
                fromStack.isItemEqual(toStack) &&
                ItemStack.areItemStackTagsEqual(fromStack, toStack) &&
                itemsMoved > 0) {
                toStack.grow(from.decrStackSize(itemsMoved).count)
            } else return false
        } else if (to.isItemValid(fromStack)) {
            to.putStack(from.decrStackSize(itemsMoved))
            if (maxStackSize == 0) {
                // Special case: we have an inventory with "phantom/ghost stacks", i.e.
                // zero size stacks, usually used for configuring machinery. In that
                // case we stop early if whatever we're shift clicking is already in a
                // slot of the target inventory. This workaround can be problematic if
                // an inventory has both real and phantom slots, but we don't have
                // something like that, yet, so hey.
                return true
            }
        } else return false

        to.onSlotChanged()
        from.onSlotChanged()
        return false
    }

    protected fun fillOrder(backFill: Boolean): List<Int> {
        val indices = if (backFill) inventorySlots.indices.reversed() else inventorySlots.indices.toList()
        return indices.sortedBy { i ->
            when (val slot = inventorySlots[i]) {
                is Slot -> if (slot.hasStack) -1 else when (slot) {
                    is ComponentSlot -> slot.tier
                    else -> 99
                }
                else -> 99
            }
        }
    }

    protected open fun tryTransferStackInSlot(from: Slot, intoPlayerInventory: Boolean) {
        for (i in fillOrder(intoPlayerInventory)) {
            val slot = inventorySlots[i]
            if (slot is Slot && tryMoveAllSlotToSlot(from, slot)) {
                return
            }
        }
    }

    fun addSlotToContainer(x: Int, y: Int, slot: String = li.cil.oc.common.Slot.Any, tier: Int = Tier.Any) {
        val index = inventorySlots.size
        addSlotToContainer(StaticComponentSlot(this, otherInventory, index, x, y, slot, tier))
    }

    fun addSlotToContainer(x: Int, y: Int, info: Array<Array<InventorySlot>>, containerTierGetter: () -> Int) {
        val index = inventorySlots.size
        addSlotToContainer(DynamicComponentSlot(this, otherInventory, index, x, y, { slot -> info[slot.containerTierGetter()][slot.slotIndex] }, containerTierGetter))
    }

    fun addSlotToContainer(x: Int, y: Int, info: (DynamicComponentSlot) -> InventorySlot) {
        val index = inventorySlots.size
        addSlotToContainer(DynamicComponentSlot(this, otherInventory, index, x, y, info) { Tier.One })
    }

    /** Render player inventory at the specified coordinates. */
    protected fun addPlayerInventorySlots(left: Int, top: Int) {
        // Show the inventory proper. Start at plus one to skip hot bar.
        for (slotY in 1 until playerInventorySizeY) {
            for (slotX in 0 until playerInventorySizeX) {
                val index = slotX + slotY * playerInventorySizeX
                val x = left + slotX * slotSize
                // Compensate for hot bar offset.
                val y = top + (slotY - 1) * slotSize
                addSlotToContainer(Slot(playerInventory, index, x, y))
            }
        }

        // Show the quick slot bar below the internal inventory.
        val quickBarSpacing = 4
        for (index in 0 until playerInventorySizeX) {
            val x = left + index * slotSize
            val y = top + slotSize * (playerInventorySizeY - 1) + quickBarSpacing
            addSlotToContainer(Slot(playerInventory, index, x, y))
        }
    }

    protected fun sendWindowProperty(id: Int, value: Int) {
        for (listener in listeners) {
            listener.sendWindowProperty(this, id, value)
        }
    }

    override fun detectAndSendChanges() {
        super.detectAndSendChanges()
        if (SideTracker.isServer()) {
            val nbt = NBTTagCompound()
            detectCustomDataChanges(nbt)
            for (entry in listeners) {
                when (entry) {
                    is FakePlayer -> { } // Nope
                    is EntityPlayerMP -> ServerPacketSender.sendContainerUpdate(this, nbt, entry)
                }
            }
        }
    }

    // Used for custom value synchronization, because shorts simply don't cut it most of the time.
    protected open fun detectCustomDataChanges(nbt: NBTTagCompound) {
        val delta = synchronizedData.getDelta()
        if (delta != null && !delta.isEmpty) {
            nbt.setTag("delta", delta)
        } else if (System.currentTimeMillis() - lastSync > 250) {
            nbt.setTag("delta", synchronizedData)
            lastSync = Long.MAX_VALUE
        }
    }

    open fun updateCustomData(nbt: NBTTagCompound) {
        if (nbt.hasKey("delta")) {
            val delta = nbt.getCompoundTag("delta")
            for (key in delta.keySet) {
                synchronizedData.setTag(key, delta.getTag(key))
            }
        }
    }

    protected inner class SynchronizedData : NBTTagCompound() {
        private var delta = NBTTagCompound()

        fun getDelta(): NBTTagCompound? = synchronized(this) {
            if (delta.isEmpty) null
            else {
                val result = delta
                delta = NBTTagCompound()
                result
            }
        }

        override fun setTag(key: String, value: NBTBase) = synchronized(this) {
            if (value != getTag(key)) delta.setTag(key, value)
            super.setTag(key, value)
        }

        override fun setByte(key: String, value: Byte) = synchronized(this) {
            if (value != getByte(key)) delta.setByte(key, value)
            super.setByte(key, value)
        }

        override fun setShort(key: String, value: Short) = synchronized(this) {
            if (value != getShort(key)) delta.setShort(key, value)
            super.setShort(key, value)
        }

        override fun setInteger(key: String, value: Int) = synchronized(this) {
            if (value != getInteger(key)) delta.setInteger(key, value)
            super.setInteger(key, value)
        }

        override fun setLong(key: String, value: Long) = synchronized(this) {
            if (value != getLong(key)) delta.setLong(key, value)
            super.setLong(key, value)
        }

        override fun setFloat(key: String, value: Float) = synchronized(this) {
            if (value != getFloat(key)) delta.setFloat(key, value)
            super.setFloat(key, value)
        }

        override fun setDouble(key: String, value: Double) = synchronized(this) {
            if (value != getDouble(key)) delta.setDouble(key, value)
            super.setDouble(key, value)
        }

        override fun setString(key: String, value: String) = synchronized(this) {
            if (value != getString(key)) delta.setString(key, value)
            super.setString(key, value)
        }

        override fun setByteArray(key: String, value: ByteArray) = synchronized(this) {
            if (!value.contentEquals(getByteArray(key))) delta.setByteArray(key, value)
            super.setByteArray(key, value)
        }

        override fun setIntArray(key: String, value: IntArray) = synchronized(this) {
            if (!value.contentEquals(getIntArray(key))) delta.setIntArray(key, value)
            super.setIntArray(key, value)
        }

        override fun setBoolean(key: String, value: Boolean) = synchronized(this) {
            if (value != getBoolean(key)) delta.setBoolean(key, value)
            super.setBoolean(key, value)
        }
    }

    protected val synchronizedData = SynchronizedData()
}
