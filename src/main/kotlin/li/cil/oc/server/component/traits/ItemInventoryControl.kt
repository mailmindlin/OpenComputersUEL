package li.cil.oc.server.component.traits

import li.cil.oc.api.Driver
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.ExtendedArguments.checkSlot
import li.cil.oc.util.ExtendedArguments.optItemCount
import li.cil.oc.util.InventoryUtils
import net.minecraft.item.ItemStack
import net.minecraftforge.items.IItemHandler

interface ItemInventoryControl : InventoryAware {
    @Callback(doc = "function(slot:number):number -- The size of an item inventory in the specified slot.")
    fun getItemInventorySize(context: Context, args: Arguments): Array<Any?> {
        return withItemInventory(args.checkSlot(inventory, 0)) { itemInventory ->
            result(itemInventory.slots)
        }
    }

    @Callback(doc = "function(inventorySlot:number, slot:number[, count:number=64]):number -- Drops an item from the selected slot into the specified slot in the item inventory.")
    fun dropIntoItemInventory(context: Context, args: Arguments): Array<Any?> {
        return withItemInventory(args.checkSlot(inventory, 0)) { itemInventory ->
            val slot = args.checkSlot(itemInventory, 1)
            val count = args.optItemCount(2)
            val extracted = InventoryUtils.extractFromInventorySlot(
                { itemStack, simulate ->
                    InventoryUtils.insertIntoInventorySlot(itemStack, itemInventory, slot, simulate = simulate)
                },
                inventory,
                null,
                selectedSlot,
                count
            )
            result(extracted)
        }
    }

    @Callback(doc = "function(inventorySlot:number, slot:number[, count:number=64]):number -- Sucks an item out of the specified slot in the item inventory.")
    fun suckFromItemInventory(context: Context, args: Arguments): Array<Any?> {
        return withItemInventory(args.checkSlot(inventory, 0)) { itemInventory ->
            val slot = args.checkSlot(itemInventory, 1)
            val count = args.optItemCount(2)
            val extracted = InventoryUtils.extractFromInventorySlot(
                { itemStack, simulate ->
                    InventoryUtils.insertIntoInventory(
                        itemStack,
                        InventoryUtils.asItemHandler(inventory),
                        slots = insertionSlots,
                        simulate = simulate
                    )
                },
                itemInventory,
                slot,
                count
            )
            result(extracted)
        }
    }

    fun withItemInventory(slot: Int, f: (IItemHandler) -> Array<Any?>): Array<Any?> {
        val stack = inventory.getStackInSlot(slot)
        if (stack is ItemStack) {
            val itemHandler = Driver.itemHandlerFor(stack, fakePlayer)
            if (itemHandler is IItemHandler) {
                return f(itemHandler)
            }
        }
        return result(0, "no item inventory")
    }
}
