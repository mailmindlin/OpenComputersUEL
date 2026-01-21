package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.ExtendedArguments.checkSlot
import li.cil.oc.util.ExtendedArguments.optItemCount
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.StackOption
import net.minecraft.item.ItemStack

interface InventoryControl : InventoryAware {
    @Callback(doc = "function():number -- The size of this device's internal inventory.")
    fun inventorySize(context: Context, args: Arguments): Array<Any?> {
        return result(inventory.sizeInventory)
    }

    @Callback(doc = "function([slot:number]):number -- Get the currently selected slot; set the selected slot if specified.")
    fun select(context: Context, args: Arguments): Array<Any?> {
        val slot = optSlot(args, 0)
        if (slot != selectedSlot) {
            selectedSlot = slot
        }
        return result(selectedSlot + 1)
    }

    @Callback(direct = true, doc = "function([slot:number]):number -- Get the number of items in the specified slot, otherwise in the selected slot.")
    fun count(context: Context, args: Arguments): Array<Any?> {
        val slot = optSlot(args, 0)
        val count = when (val stack = stackInSlot(slot)) {
            is StackOption.SomeStack -> stack.stack.count
            else -> 0
        }
        return result(count)
    }

    @Callback(direct = true, doc = "function([slot:number]):number -- Get the remaining space in the specified slot, otherwise in the selected slot.")
    fun space(context: Context, args: Arguments): Array<Any?> {
        val slot = optSlot(args, 0)
        val space = when (val stack = stackInSlot(slot)) {
            is StackOption.SomeStack -> {
                val maxSize = minOf(inventory.inventoryStackLimit, stack.stack.maxStackSize)
                maxSize - stack.stack.count
            }
            else -> inventory.inventoryStackLimit
        }
        return result(space)
    }

    @Callback(doc = "function(otherSlot:number[, checkNBT:boolean=false]):boolean -- Compare the contents of the selected slot to the contents of the specified slot.")
    fun compareTo(context: Context, args: Arguments): Array<Any?> {
        val slot = args.checkSlot(inventory, 0)
        val checkNBT = args.optBoolean(1, false)

        val result = when {
            stackInSlot(selectedSlot) is StackOption.SomeStack && stackInSlot(slot) is StackOption.SomeStack -> {
                val stackA = (stackInSlot(selectedSlot) as StackOption.SomeStack).stack
                val stackB = (stackInSlot(slot) as StackOption.SomeStack).stack
                InventoryUtils.haveSameItemType(stackA, stackB, checkNBT)
            }
            stackInSlot(selectedSlot) is StackOption.EmptyStack && stackInSlot(slot) is StackOption.EmptyStack -> {
                true
            }
            else -> false
        }

        return result(result)
    }

    @Callback(doc = "function(toSlot:number[, amount:number]):boolean -- Move up to the specified amount of items from the selected slot into the specified slot.")
    fun transferTo(context: Context, args: Arguments): Array<Any?> {
        val slot = args.checkSlot(inventory, 0)
        val count = args.optItemCount(1)

        if (slot == selectedSlot || count == 0) {
            return result(true)
        }

        val transferred = when {
            stackInSlot(selectedSlot) is StackOption.SomeStack && stackInSlot(slot) is StackOption.SomeStack -> {
                val from = (stackInSlot(selectedSlot) as StackOption.SomeStack).stack
                val to = (stackInSlot(slot) as StackOption.SomeStack).stack

                if (InventoryUtils.haveSameItemType(from, to, checkNBT = true)) {
                    val space = minOf(inventory.inventoryStackLimit, to.maxStackSize) - to.count
                    val amount = minOf(count, minOf(space, from.count))
                    if (amount > 0) {
                        from.shrink(amount)
                        to.grow(amount)
                        assert(from.count >= 0)
                        if (from.count == 0) {
                            inventory.setInventorySlotContents(selectedSlot, ItemStack.EMPTY)
                        }
                        inventory.markDirty()
                        true
                    } else {
                        false
                    }
                } else if (count >= from.count) {
                    inventory.setInventorySlotContents(slot, from)
                    inventory.setInventorySlotContents(selectedSlot, to)
                    true
                } else {
                    false
                }
            }
            stackInSlot(selectedSlot) is StackOption.SomeStack && stackInSlot(slot) is StackOption.EmptyStack -> {
                inventory.setInventorySlotContents(slot, inventory.decrStackSize(selectedSlot, count))
                true
            }
            else -> false
        }

        return result(transferred)
    }
}
