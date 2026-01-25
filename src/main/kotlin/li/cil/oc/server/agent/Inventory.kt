package li.cil.oc.server.agent

import li.cil.oc.api.internal.Agent
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.StackOption
import li.cil.oc.util.notEmpty
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList

class Inventory(playerEntity: EntityPlayer, val agent: Agent) : InventoryPlayer(playerEntity) {

    private val selectedItemStack: ItemStack get() = agent.mainInventory().getStackInSlot(agent.selectedSlot())

    private val inventorySlots: List<Int> get() = (agent.selectedSlot() until sizeInventory) + (0 until agent.selectedSlot())

    override fun getCurrentItem(): ItemStack = agent.equipmentInventory().getStackInSlot(0)

    override fun getFirstEmptyStack(): Int {
        return if (selectedItemStack.isEmpty()) agent.selectedSlot()
        else inventorySlots.firstOrNull { getStackInSlot(it).isEmpty() } ?: -1
    }

    override fun changeCurrentItem(direction: Int) {}

    override fun clearMatchingItems(item: Item?, damage: Int, count: Int, tag: NBTTagCompound?): Int = 0

    override fun decrementAnimations() {
        for (slot in 0 until sizeInventory) {
            val stack = getStackInSlot(slot).notEmpty() ?: continue
            try {
                stack.updateAnimation(agent.world(), if (!agent.world().isRemote) agent.player() else null, slot, slot == 0)
            } catch (ignored: NullPointerException) {
                // Client side item updates that need a player instance...
            }
        }
    }

    override fun addItemStackToInventory(stack: ItemStack): Boolean {
        return InventoryUtils.insertIntoInventory(stack, InventoryUtils.asItemHandler(this), slots = inventorySlots)
    }

    override fun canHarvestBlock(state: IBlockState): Boolean =
        state.material.isToolNotRequired || (!getCurrentItem().isEmpty() && getCurrentItem().canHarvestBlock(state))

    override fun getDestroySpeed(state: IBlockState): Float =
        if (getCurrentItem().isEmpty) 1f else getCurrentItem().getDestroySpeed(state)

    override fun writeToNBT(nbt: NBTTagList): NBTTagList = nbt

    override fun readFromNBT(nbt: NBTTagList) {}

    override fun armorItemInSlot(slot: Int): ItemStack = ItemStack.EMPTY

    override fun damageArmor(damage: Float) {}

    override fun dropAllItems() {}

    override fun hasItemStack(stack: ItemStack): Boolean =
        (0 until sizeInventory).map { getStackInSlot(it) }.filter { !it.isEmpty() }.any { it.isItemEqual(stack) }

    override fun copyInventory(from: InventoryPlayer) {}

    // IInventory

    override fun getSizeInventory(): Int = agent.mainInventory().sizeInventory

    override fun getStackInSlot(slot: Int): ItemStack =
        if (slot < 0) agent.equipmentInventory().getStackInSlot(slot.inv())
        else agent.mainInventory().getStackInSlot(slot)

    override fun decrStackSize(slot: Int, amount: Int): ItemStack =
        if (slot < 0) agent.equipmentInventory().decrStackSize(slot.inv(), amount)
        else agent.mainInventory().decrStackSize(slot, amount)

    override fun removeStackFromSlot(slot: Int): ItemStack =
        if (slot < 0) agent.equipmentInventory().removeStackFromSlot(slot.inv())
        else agent.mainInventory().removeStackFromSlot(slot)

    override fun setInventorySlotContents(slot: Int, stack: ItemStack) {
        if (slot < 0) agent.equipmentInventory().setInventorySlotContents(slot.inv(), stack)
        else agent.mainInventory().setInventorySlotContents(slot, stack)
    }

    override fun getName(): String = agent.mainInventory().name

    override fun getInventoryStackLimit(): Int = agent.mainInventory().inventoryStackLimit

    override fun markDirty() = agent.mainInventory().markDirty()

    override fun isUsableByPlayer(player: EntityPlayer): Boolean = agent.mainInventory().isUsableByPlayer(player)

    override fun isItemValidForSlot(slot: Int, stack: ItemStack): Boolean =
        if (slot < 0) agent.equipmentInventory().isItemValidForSlot(slot.inv(), stack)
        else agent.mainInventory().isItemValidForSlot(slot, stack)
}
