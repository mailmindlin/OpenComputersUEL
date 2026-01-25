package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.*
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraftforge.items.IItemHandler

interface InventoryWorldControlMk2 : InventoryAware, WorldAware, SideRestricted {
    @Callback(doc = """function(facing:number, slot:number[, count:number[, fromSide:number]]):boolean -- Drops the selected item stack into the specified slot of an inventory.""")
    fun dropIntoSlot(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        val count = args.optItemCount(2)
        val fromSide = args.optSideAny(3, facing.opposite)
        val stack = inventory.getStackInSlot(selectedSlot)

        if (!stack.isEmpty && stack.count > 0) {
            return withInventory(position.offset(facing), fromSide) { targetInventory ->
                val slot = args.checkSlot(targetInventory, 1)
                if (!InventoryUtils.insertIntoInventorySlot(stack, targetInventory, slot, count)) {
                    // Cannot drop into that inventory.
                    return@withInventory result(false, "inventory full/invalid slot")
                } else if (stack.count == 0) {
                    // Dropped whole stack.
                    this.inventory.setInventorySlotContents(selectedSlot, ItemStack.EMPTY)
                } else {
                    // Dropped partial stack.
                    this.inventory.markDirty()
                }

                context.pause(Settings.get.dropDelay)
                result(true)
            }
        }

        return result(false)
    }

    @Callback(doc = """function(facing:number, slot:number[, count:number[, fromSide:number]]):boolean -- Sucks items from the specified slot of an inventory.""")
    fun suckFromSlot(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        val count = args.optItemCount(2)
        val fromSide = args.optSideAny(3, facing.opposite)

        return withInventory(position.offset(facing), fromSide) { targetInventory ->
            val slot = args.checkSlot(targetInventory, 1)
            val extracted = InventoryUtils.extractFromInventorySlot(
                { itemStack, simulate ->
                    InventoryUtils.insertIntoInventory(
                        itemStack,
                        InventoryUtils.asItemHandler(this.inventory),
                        slots = insertionSlots,
                        simulate = simulate
                    )
                },
                targetInventory,
                slot,
                count
            )

            if (extracted > 0) {
                context.pause(Settings.get.suckDelay)
                result(extracted)
            } else {
                result(false)
            }
        }
    }

    fun withInventory(blockPos: BlockPosition, fromSide: EnumFacing, f: (IItemHandler) -> Array<Any?>): Array<Any?> {
        val inventorySource = InventoryUtils.inventorySourceAt(blockPos, fromSide)
        return if (inventorySource != null && mayInteract(inventorySource)) {
            f(inventorySource.inventory)
        } else {
            result(null, "no inventory")
        }
    }
}
