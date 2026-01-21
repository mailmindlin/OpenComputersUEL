package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Robot
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.InventoryUtils
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.*
import net.minecraft.item.crafting.CraftingManager

sealed class UpgradeCrafting(val host: EnvironmentHost) : ManagedEnvironmentKt(), DeviceInfoKt {
    private val robot: Robot
        get() = host as Robot

    override val node = Network.newNode(this, Visibility.Network)
        .withComponent("crafting")
        .create()

    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Generic,
        DeviceAttribute.Description to "Assembly controller",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "MultiCombinator-9S"
    )

    @Callback(doc = "function([count:number]):number -- Tries to craft the specified number of items in the top left area of the inventory.")
    fun craft(context: Context, args: Arguments): Array<Any?> {
        val count = args.optInteger(0, 64).coerceIn(0, 64)
        return result(*CraftingInventory().craft(count).toTypedArray())
    }

    private inner class CraftingInventory : InventoryCrafting(object : Container() {
        override fun canInteractWith(player: EntityPlayer) = true
    }, 3, 3) {
        fun craft(wantedCount: Int): List<Any?> {
            val player = (host as Robot).player()
            copyItemsFromHost(player.inventory)
            var countCrafted = 0
            val initialCraft = CraftingManager.findMatchingRecipe(this, (host as Robot).world())
            if (initialCraft != null) {
                fun tryCraft(): Boolean {
                    val craft = CraftingManager.findMatchingRecipe(this, (host as Robot).world())
                    if (craft == null || craft != initialCraft) {
                        return false
                    }

                    val craftResult = InventoryCraftResult()
                    val craftingSlot = SlotCrafting(player, this, craftResult, 0, 0, 0)
                    val craftedResult = craft.getCraftingResult(this)
                    craftResult.setInventorySlotContents(0, craftedResult)
                    if (!craftingSlot.hasStack)
                        return false

                    val stack = craftingSlot.decrStackSize(1)
                    countCrafted += maxOf(stack.count, 1)
                    val taken = craftingSlot.onTake(player, stack)
                    copyItemsToHost(player.inventory)
                    if (taken.count > 0) {
                        InventoryUtils.addToPlayerInventory(taken, player)
                    }
                    copyItemsFromHost(player.inventory)
                    return true
                }
                while (countCrafted < wantedCount && tryCraft()) {
                    //
                }
            }
            return listOf(countCrafted > 0, countCrafted)
        }

        fun copyItemsFromHost(inventory: IInventory) {
            for (slot in 0 until sizeInventory) {
                val stack = inventory.getStackInSlot(toParentSlot(slot))
                setInventorySlotContents(slot, stack)
            }
        }

        fun copyItemsToHost(inventory: IInventory) {
            for (slot in 0 until sizeInventory) {
                inventory.setInventorySlotContents(toParentSlot(slot), getStackInSlot(slot))
            }
        }

        private fun toParentSlot(slot: Int): Int {
            val col = slot % 3
            val row = slot / 3
            return row * 4 + col
        }
    }
}
