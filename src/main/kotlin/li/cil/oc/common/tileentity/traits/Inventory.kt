package li.cil.oc.common.tileentity.traits

import li.cil.oc.common.inventory.Inventory.Companion.load
import li.cil.oc.common.inventory.Inventory.Companion.save
import li.cil.oc.common.tileentity.behaviors.Behavior
import li.cil.oc.common.tileentity.behaviors.NbtSeriailzable
import li.cil.oc.common.inventory.Inventory as InventoryInterface
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.text.ITextComponent

interface Inventory : TileEntityTrait, InventoryInterface {
    override val items: Array<ItemStack> get() = inventoryDelegate.items

    val inventoryDelegate: Delegate

    // ----------------------------------------------------------------------- //

    class Delegate(val tile: Inventory): NbtSeriailzable {
        val items: Array<ItemStack> = Array(tile.sizeInventory) { ItemStack.EMPTY }
        override fun readFromNBTForServer(nbt: NBTTagCompound) {
            tile.load(nbt)
        }

        override fun writeToNBTForServer(nbt: NBTTagCompound) {
            tile.save(nbt)
        }
    }

    override fun getDisplayName(): ITextComponent = super<InventoryInterface>.getDisplayName()

    // ----------------------------------------------------------------------- //

    override fun isUsableByPlayer(player: EntityPlayer): Boolean =
        player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) <= 64

    // ----------------------------------------------------------------------- //

    fun dropSlot(slot: Int, count: Int = inventoryStackLimit, direction: EnumFacing? = null): Boolean =
        InventoryUtils.dropSlot(BlockPosition(x, y, z, world), this, slot, count, direction)

    fun dropAllSlots() =
        InventoryUtils.dropAllSlots(BlockPosition(x, y, z, world), this)

    fun spawnStackInWorld(stack: ItemStack, direction: EnumFacing? = null) {
        InventoryUtils.spawnStackInWorld(BlockPosition(x, y, z, world), stack, direction)
    }
}
