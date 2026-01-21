package li.cil.oc.common.container

import li.cil.oc.common
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

abstract class ComponentSlot(inventory: IInventory, index: Int, x: Int, y: Int) : Slot(inventory, index, x, y) {
    abstract val container: Player

    abstract val slot: String

    abstract val tier: Int

    abstract val tierIcon: ResourceLocation?

    var changeListener: ((Slot) -> Unit)? = null

    // ----------------------------------------------------------------------- //

    open fun hasBackground(): Boolean = backgroundLocation != null

    @SideOnly(Side.CLIENT)
    override fun isEnabled(): Boolean = slot != common.Slot.None && tier != common.Tier.None && super.isEnabled()

    override fun isItemValid(stack: ItemStack): Boolean = inventory.isItemValidForSlot(slotIndex, stack)

    override fun onTake(player: EntityPlayer, stack: ItemStack): ItemStack {
        for (slot in container.inventorySlots) {
            if (slot is ComponentSlot) {
                slot.clearIfInvalid(player)
            }
        }
        return super.onTake(player, stack)
    }

    override fun putStack(stack: ItemStack) {
        super.putStack(stack)
        val inv = inventory
        if (inv is common.tileentity.traits.PlayerInputAware) {
            inv.onSetInventorySlotContents(container.playerInventory.player, slotIndex, stack)
        }
    }

    override fun onSlotChanged() {
        super.onSlotChanged()
        for (slot in container.inventorySlots) {
            if (slot is ComponentSlot) {
                slot.clearIfInvalid(container.playerInventory.player)
            }
        }
        changeListener?.invoke(this)
    }

    protected open fun clearIfInvalid(player: EntityPlayer) {}
}
