package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.ExtendedArguments.optItemCount
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.StackOption
import net.minecraft.entity.item.EntityItem
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.item.ItemTossEvent
import net.minecraftforge.fml.common.eventhandler.Event

interface InventoryWorldControl : InventoryAware, WorldAware, SideRestricted {
    @Callback(doc = "function(side:number[, fuzzy:boolean=false]):boolean -- Compare the block on the specified side with the one in the selected slot. Returns true if equal.")
    fun compare(context: Context, args: Arguments): Array<Any?> {
        val side = checkSideForAction(args, 0)

        when (val stackOption = stackInSlot(selectedSlot)) {
            is StackOption.SomeStack -> {
                val stack = stackOption.stack
                val item = stack.item

                if (item is ItemBlock) {
                    val blockPos = position.offset(side).toBlockPos()
                    val state = world.getBlockState(blockPos)
                    val idMatches = item.block == state.block
                    val fuzzy = args.optBoolean(1, false)
                    val subTypeMatches = fuzzy || !item.hasSubtypes ||
                                        item.getMetadata(stack.itemDamage) == state.block.getMetaFromState(state)
                    return result(idMatches && subTypeMatches)
                }
            }
            else -> {}
        }

        return result(false)
    }

    @Callback(doc = "function(side:number[, count:number=64]):boolean -- Drops items from the selected slot towards the specified side.")
    fun drop(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        val count = args.optItemCount(1)
        val stack = inventory.getStackInSlot(selectedSlot)

        if (!stack.isEmpty && stack.count > 0) {
            val blockPos = position.offset(facing)
            val inventorySource = InventoryUtils.inventorySourceAt(blockPos, facing.opposite)

            if (inventorySource != null && mayInteract(inventorySource)) {
                if (!InventoryUtils.insertIntoInventory(stack, inventorySource.inventory, count)) {
                    // Cannot drop into that inventory.
                    return result(false, "inventory full")
                } else if (stack.count == 0) {
                    // Dropped whole stack.
                    inventory.setInventorySlotContents(selectedSlot, ItemStack.EMPTY)
                } else {
                    // Dropped partial stack.
                    inventory.markDirty()
                }
            } else {
                // No inventory to drop into, drop into the world.
                val dropped = inventory.decrStackSize(selectedSlot, count)
                val validator: (EntityItem) -> Boolean = { item ->
                    val event = ItemTossEvent(item, fakePlayer)
                    val canceled = MinecraftForge.EVENT_BUS.post(event)
                    val denied = event.hasResult() && event.result == Event.Result.DENY
                    !canceled && !denied
                }

                if (!dropped.isEmpty) {
                    if (InventoryUtils.spawnStackInWorld(position, dropped, facing, validator) == null) {
                        fakePlayer.inventory.addItemStackToInventory(dropped)
                    }
                }
            }

            context.pause(Settings.get.dropDelay)
            return result(true)
        }

        return result(false)
    }

    /**
     * @param facing items to suck from
     * @return the number of items sucked
     */
    fun suckFromItems(facing: EnumFacing): Int {
        for (entity in suckableItems(facing)) {
            if (!entity.isDead && !entity.cannotPickup()) {
                val stack = entity.item
                val size = stack.count
                onSuckCollect(entity)
                if (stack.count < size) {
                    return size - stack.count
                } else if (entity.isDead) {
                    return size
                }
            }
        }
        return 0
    }

    @Callback(doc = "function(side:number[, count:number=64]):boolean -- Suck up items from the specified side.")
    fun suck(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        val count = args.optItemCount(1)

        val blockPos = position.offset(facing)
        var extracted = 0

        val inventorySource = InventoryUtils.inventorySourceAt(blockPos, facing.opposite)
        if (inventorySource != null && mayInteract(inventorySource)) {
            extracted = InventoryUtils.extractAnyFromInventory(
                { itemStack, simulate ->
                    InventoryUtils.insertIntoInventory(
                        itemStack,
                        InventoryUtils.asItemHandler(this.inventory),
                        slots = insertionSlots,
                        simulate = simulate
                    )
                },
                inventorySource.inventory,
                count
            )
        }

        if (extracted <= 0) {
            extracted = suckFromItems(facing)
        }

        return if (extracted <= 0) {
            result(false)
        } else {
            context.pause(Settings.get.suckDelay)
            result(extracted)
        }
    }

    fun suckableItems(side: EnumFacing): List<EntityItem> {
        return entitiesOnSide(EntityItem::class.java, side)
    }

    fun onSuckCollect(entity: EntityItem) {
        entity.onCollideWithPlayer(fakePlayer)
    }
}
