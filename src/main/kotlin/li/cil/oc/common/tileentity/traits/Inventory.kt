package li.cil.oc.common.tileentity.traits

import li.cil.oc.common.inventory
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.InventoryUtils
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.text.ITextComponent

abstract class Inventory : TileEntity(), inventory.Inventory {
    private val inventory: Array<ItemStack> by lazy { Array(getSizeInventory()) { ItemStack.EMPTY } }

    override fun items(): Array<ItemStack> = inventory

    // ----------------------------------------------------------------------- //

    override fun getDisplayName(): ITextComponent = super<inventory.Inventory>.getDisplayName()

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        load(nbt)
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        save(nbt)
    }

    // ----------------------------------------------------------------------- //

    override fun isUsableByPlayer(player: EntityPlayer): Boolean =
        player.getDistanceSq(x + 0.5, y + 0.5, z + 0.5) <= 64

    // ----------------------------------------------------------------------- //

    fun dropSlot(slot: Int, count: Int = inventoryStackLimit, direction: EnumFacing? = null): Boolean =
        InventoryUtils.dropSlot(BlockPosition(x, y, z, getWorld()), this, slot, count, if (direction != null) java.util.Optional.of(direction) else java.util.Optional.empty())

    fun dropAllSlots() =
        InventoryUtils.dropAllSlots(BlockPosition(x, y, z, getWorld()), this)

    fun spawnStackInWorld(stack: ItemStack, direction: EnumFacing? = null): Unit =
        InventoryUtils.spawnStackInWorld(BlockPosition(x, y, z, getWorld()), stack, if (direction != null) java.util.Optional.of(direction) else java.util.Optional.empty())
}
