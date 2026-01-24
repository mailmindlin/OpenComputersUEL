package li.cil.oc.common.container

import li.cil.oc.client.Textures
import li.cil.oc.common.Slot as CommonSlot
import li.cil.oc.common.Tier as CommonTier
import li.cil.oc.common.entity.Drone as EntityDrone
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class Drone(playerInventory: InventoryPlayer, val drone: EntityDrone) : Player<IInventory>(playerInventory, drone.mainInventory) {
    val deltaY: Int = 0

    init {
        for (i in 0..1) {
            val y = 8 + i * slotSize - deltaY
            for (j in 0..3) {
                val x = 98 + j * slotSize
                addSlotToContainer(InventorySlot(this, otherInventory, inventorySlots.size, x, y))
            }
        }

        addPlayerInventorySlots(8, 66)
    }

    inner class InventorySlot(container: Player<IInventory>, inventory: IInventory, index: Int, x: Int, y: Int)
        : StaticComponentSlot(container, inventory, index, x, y, CommonSlot.Any, CommonTier.Any) {

        val isValid: Boolean
            get() = slotIndex in 0 until drone.mainInventory.sizeInventory

        @SideOnly(Side.CLIENT)
        override fun isEnabled(): Boolean = isValid && super.isEnabled()

        override fun getBackgroundLocation(): ResourceLocation? =
            if (isValid) super.getBackgroundLocation()
            else Textures.Icons.get(CommonTier.None)

        override fun getStack(): ItemStack =
            if (isValid) super.getStack()
            else ItemStack.EMPTY
    }
}
