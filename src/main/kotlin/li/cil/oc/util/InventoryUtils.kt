package li.cil.oc.util

import li.cil.oc.OpenComputers
import net.minecraft.entity.Entity
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.inventory.IInventory
import net.minecraft.inventory.ISidedInventory
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.IItemHandlerModifiable
import net.minecraftforge.items.wrapper.InvWrapper
import net.minecraftforge.items.wrapper.SidedInvWrapper
import kotlin.math.min

public object InventoryUtils {
    @JvmStatic
    @JvmOverloads
    fun asItemHandler(inventory: IInventory?, side: EnumFacing? = null): IItemHandlerModifiable = when {
        inventory is ISidedInventory && side != null -> SidedInvWrapper(inventory, side)
        else -> InvWrapper(inventory)
    }

    /**
     * Check if two item stacks are of equal type, ignoring the stack size.
     *
     * Optionally check for equality in NBT data.
     */
    @JvmStatic
    @JvmOverloads
    fun haveSameItemType(stackA: ItemStack, stackB: ItemStack, checkNBT: Boolean = false): Boolean =
        !stackA.isEmpty && !stackB.isEmpty &&
            stackA.item == stackB.item &&
            (!stackA.hasSubtypes || stackA.itemDamage == stackB.itemDamage) &&
            (!checkNBT || ItemStack.areItemStackTagsEqual(stackA, stackB))

    /**
     * Retrieves an actual inventory implementation for a specified world coordinate,
     * complete with a reference to the source of said implementation.
     */
    @JvmStatic
    fun inventorySourceAt(position: BlockPosition, side: EnumFacing?): InventorySource? {
        val world = position.world ?: return null
        if (!world.blockExists(position)) return null

        val tile = world.getTileEntity(position)
        return when {
            tile is TileEntity && tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side) ->
                BlockInventorySource(position, side, tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side)!!)
            tile is IInventory ->
                BlockInventorySource(position, side, asItemHandler(tile, side))
            else -> {
                world.getEntitiesWithinAABB(Entity::class.java, position.bounds)
                    .filter { e -> !e.isDead && e.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side) }
                    .map { a -> EntityInventorySource(a, side, a.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side)!!) }
                    .firstOrNull { a -> a.inventory != null }
            }
        }
    }

    /**
     * Retrieves an actual inventory implementation for a specified world coordinate.
     */
    @JvmStatic
    fun inventoryAt(position: BlockPosition, side: EnumFacing?): IItemHandler? =
        inventorySourceAt(position, side)?.inventory

    @JvmStatic
    fun anyInventorySourceAt(position: BlockPosition): InventorySource? {
        val sides = listOf(null) + EnumFacing.VALUES.toList()
        for (side in sides) {
            val inv = inventorySourceAt(position, side)
            if (inv != null) return inv
        }
        return null
    }

    @JvmStatic
    fun anyInventoryAt(position: BlockPosition): IItemHandler? =
        anyInventorySourceAt(position)?.inventory

    /**
     * Inserts a stack into an inventory.
     *
     * Only tries to insert into the specified slot. This *cannot* be
     * used to empty a slot. It can only insert stacks into empty slots and
     * merge additional items into an existing stack in the slot.
     *
     * The passed stack's size will be adjusted to reflect the number of items
     * inserted into the inventory, i.e. if 10 more items could fit into the
     * slot, the stack's size will be 10 smaller than before the call.
     *
     * This will return `true` if *at least* one item could be
     * inserted into the slot. It will return `false` if the passed
     * stack did not change. Note that it will also change the stack
     * when called with `simulate = true`.
     *
     * This takes care of handling special cases such as sided inventories,
     * maximum inventory and item stack sizes.
     *
     * The number of items inserted can be limited, to avoid unnecessary
     * changes to the inventory the stack may come from, for example.
     */
    @JvmStatic
    @JvmOverloads
    fun insertIntoInventorySlot(
        stack: ItemStack,
        inventory: IItemHandler,
        slot: Int,
        limit: Int = 64,
        simulate: Boolean = false
    ): Boolean {
        if (stack.isEmpty || limit <= 0 || stack.count <= 0) return false

        val amount = min(stack.count, limit)
        val toInsert = stack.splitStack(amount)
        val remaining = inventory.insertItem(slot, toInsert, simulate)
        val result = remaining == null || remaining.count < amount
        stack.grow(remaining?.count ?: 0)
        return result
    }

    @JvmStatic
    fun insertIntoInventorySlot(
        stack: ItemStack,
        inventory: IInventory,
        side: EnumFacing?,
        slot: Int,
        limit: Int,
        simulate: Boolean
    ): Boolean = insertIntoInventorySlot(stack, asItemHandler(inventory, side), slot, limit, simulate)

    /**
     * Extracts a stack from an inventory.
     *
     * Only tries to extract from the specified slot. This *can* be used
     * to empty a slot. It will extract items using the specified consumer method
     * which is called with the extracted stack and a simulation flag before the
     * stack in the inventory that we extract from is cleared from. This allows
     * placing back excess items with as few inventory updates as possible.
     *
     * The consumer is the only way to retrieve the actually extracted stack. It
     * is called with a separate stack instance, so it does not have to be copied
     * again.
     *
     * This will return the number of items extracted. It will return
     * zero if the stack in the slot did not change.
     *
     * This takes care of handling special cases such as sided inventories and
     * maximum stack sizes.
     *
     * The number of items extracted can be limited, to avoid unnecessary
     * changes to the inventory the stack is extracted from. Note that this could
     * also be achieved by a check in the consumer, but it saves some unnecessary
     * code repetition this way.
     */
    @JvmStatic
    @JvmOverloads
    fun extractFromInventorySlot(
        consumer: (ItemStack, Boolean) -> Unit,
        inventory: IItemHandler,
        slot: Int,
        limit: Int = 64
    ): Int {
        val stack = inventory.getStackInSlot(slot)
        if (stack.isEmpty || limit <= 0 || stack.count <= 0) return 0

        var amount = minOf(stack.maxStackSize, stack.count, limit)
        val simExtracted = inventory.extractItem(slot, amount, true)
        if (simExtracted == null || simExtracted.isEmpty) return 0

        val extracted = simExtracted.copy()
        amount = extracted.count
        consumer(extracted, true)
        val count = maxOf(amount - extracted.count, 0)
        if (count > 0) {
            val realExtracted = inventory.extractItem(slot, count, false)
            if (realExtracted != null && realExtracted.count == count) {
                consumer(realExtracted, false)
            } else {
                OpenComputers.log.warn("An IItemHandler instance acted differently between simulated and non-simulated extraction. Offender: $inventory")
                // Attempt inserting the stack anyway, to minimize world-side item loss.
                if (realExtracted != null && !realExtracted.isEmpty) {
                    consumer(realExtracted, false)
                }
            }
        }
        return count
    }

    @JvmStatic
    fun extractFromInventorySlot(
        consumer: (ItemStack, Boolean) -> Unit,
        inventory: IInventory,
        side: EnumFacing?,
        slot: Int,
        limit: Int
    ): Int = extractFromInventorySlot(consumer, asItemHandler(inventory, side), slot, limit)

    /**
     * Inserts a stack into an inventory.
     *
     * This will try to fit the stack in any and as many as necessary slots in
     * the inventory. It will first try to merge the stack in stacks already
     * present in the inventory. After that it will try to fit the stack into
     * empty slots in the inventory.
     *
     * This uses the [insertIntoInventorySlot] method, and therefore
     * handles special cases such as sided inventories and stack size limits.
     *
     * This returns `true` if at least one item was inserted. The passed
     * item stack will be adjusted to reflect the number items inserted, by
     * having its size decremented accordingly.
     */
    @JvmStatic
    @JvmOverloads
    fun insertIntoInventory(
        stack: ItemStack,
        inventory: IItemHandler,
        limit: Int = 64,
        simulate: Boolean = false,
        slots: Iterable<Int>? = null
    ): Boolean {
        if (stack.isEmpty || limit <= 0 || stack.count <= 0) return false

        var success = false
        var remaining = min(limit, stack.count)
        val range = slots ?: (0 until inventory.slots)

        for (slot in range) {
            if (remaining <= 0) break
            val previousCount = stack.count
            if (insertIntoInventorySlot(stack, inventory, slot, remaining, simulate)) {
                remaining -= previousCount - stack.count
                success = true
            }
        }

        return success
    }

    @JvmStatic
    fun insertIntoInventory(
        stack: ItemStack,
        inventory: IInventory,
        side: EnumFacing?,
        limit: Int,
        simulate: Boolean,
        slots: Iterable<Int>?
    ): Boolean = insertIntoInventory(stack, asItemHandler(inventory, side), limit, simulate, slots)

    /**
     * Extracts a slot from an inventory.
     *
     * This will try to extract a stack from any inventory slot. It will iterate
     * all slots until an item can be extracted from a slot.
     *
     * This uses the [extractFromInventorySlot] method, and therefore
     * handles special cases such as sided inventories and stack size limits.
     *
     * This returns the number of items extracted.
     */
    @JvmStatic
    @JvmOverloads
    fun extractAnyFromInventory(
        consumer: (ItemStack, Boolean) -> Unit,
        inventory: IItemHandler,
        limit: Int = 64
    ): Int {
        for (slot in 0 until inventory.slots) {
            val extracted = extractFromInventorySlot(consumer, inventory, slot, limit)
            if (extracted > 0) return extracted
        }
        return 0
    }

    @JvmStatic
    fun extractAnyFromInventory(
        consumer: (ItemStack, Boolean) -> Unit,
        inventory: IInventory,
        side: EnumFacing?,
        limit: Int
    ): Int = extractAnyFromInventory(consumer, asItemHandler(inventory, side), limit)

    /**
     * Extracts an item stack from an inventory.
     *
     * This will try to remove items of the same type as the specified item stack
     * up to the number of the stack's size for all slots in the specified inventory.
     * If exact is true, the items collated will also match meta data
     *
     * This uses the [extractFromInventorySlot] method, and therefore
     * handles special cases such as sided inventories and stack size limits.
     */
    @JvmStatic
    @JvmOverloads
    fun extractFromInventory(
        stack: ItemStack,
        inventory: IItemHandler,
        simulate: Boolean = false,
        exact: Boolean = true
    ): ItemStack {
        val remaining = stack.copy()
        for (slot in 0 until inventory.slots) {
            if (remaining.count <= 0) break
            extractFromInventorySlot({ stackInInv, simulateInsert ->
                if (!stackInInv.isEmpty && remaining.item == stackInInv.item &&
                    (!exact || haveSameItemType(remaining, stackInInv, checkNBT = true))) {
                    val transferred = min(stackInInv.count, remaining.count)
                    if (!simulateInsert) {
                        remaining.shrink(transferred)
                    }
                    if (simulateInsert || !simulate) {
                        stackInInv.shrink(transferred)
                    }
                }
            }, inventory, slot, limit = remaining.count)
        }
        return remaining
    }

    @JvmStatic
    fun extractFromInventory(
        stack: ItemStack,
        inventory: IInventory,
        side: EnumFacing?,
        simulate: Boolean,
        exact: Boolean
    ): ItemStack = extractFromInventory(stack, asItemHandler(inventory, side), simulate, exact)

    /**
     * Utility method for calling [insertIntoInventory] on an inventory
     * in the world.
     */
    @JvmStatic
    @JvmOverloads
    fun insertIntoInventoryAt(
        stack: ItemStack,
        position: BlockPosition,
        side: EnumFacing? = null,
        limit: Int = 64,
        simulate: Boolean = false
    ): Boolean {
        val inventory = inventoryAt(position, side) ?: return false
        return insertIntoInventory(stack, inventory, limit, simulate)
    }

    /**
     * Utility method for calling [extractFromInventory] on an inventory
     * in the world.
     */
    @JvmStatic
    @JvmOverloads
    fun getExtractorFromInventoryAt(
        consumer: (ItemStack, Boolean) -> Unit,
        position: BlockPosition,
        side: EnumFacing?,
        limit: Int = 64
    ): (() -> Int)? {
        val inventory = inventoryAt(position, side) ?: return null
        return { extractAnyFromInventory(consumer, inventory, limit) }
    }

    /**
     * Transfers some items between two inventories.
     *
     * This will try to extract up the specified number of items from any inventory,
     * then insert it into the specified sink inventory. If the insertion fails, the
     * items will remain in the source inventory.
     *
     * This uses the [extractFromInventory] and [insertIntoInventory]
     * methods, and therefore handles special cases such as sided inventories and
     * stack size limits.
     *
     * This returns the number of items transferred.
     */
    @JvmStatic
    @JvmOverloads
    fun transferBetweenInventories(
        source: IItemHandler,
        sink: IItemHandler,
        limit: Int = 64
    ): Int = extractAnyFromInventory({ stack, simulate -> insertIntoInventory(stack, sink, limit, simulate) }, source, limit)

    @JvmStatic
    fun transferBetweenInventories(
        source: IInventory,
        sourceSide: EnumFacing?,
        sink: IInventory,
        sinkSide: EnumFacing?,
        limit: Int
    ): Int = transferBetweenInventories(asItemHandler(source, sourceSide), asItemHandler(sink, sinkSide), limit)

    /**
     * Like [transferBetweenInventories] but moving between specific slots.
     */
    @JvmStatic
    @JvmOverloads
    fun transferBetweenInventoriesSlots(
        source: IItemHandler,
        sourceSlot: Int,
        sink: IItemHandler,
        sinkSlot: Int? = null,
        limit: Int = 64
    ): Int = when (sinkSlot) {
        null -> extractFromInventorySlot(
            { stack, simulate -> insertIntoInventory(stack, sink, limit, simulate) },
            source, sourceSlot, limit
        )
        else -> extractFromInventorySlot(
            { stack, simulate -> insertIntoInventorySlot(stack, sink, sinkSlot, limit, simulate) },
            source, sourceSlot, limit
        )
    }

    @JvmStatic
    fun transferBetweenInventoriesSlots(
        source: IInventory,
        sourceSide: EnumFacing?,
        sourceSlot: Int,
        sink: IInventory,
        sinkSide: EnumFacing?,
        sinkSlot: Int?,
        limit: Int
    ): Int = transferBetweenInventoriesSlots(
        asItemHandler(source, sourceSide),
        sourceSlot,
        asItemHandler(sink, sinkSide),
        sinkSlot,
        limit
    )

    /**
     * Utility method for calling [transferBetweenInventories] on inventories
     * in the world.
     */
    @JvmStatic
    @JvmOverloads
    fun getTransferBetweenInventoriesAt(
        source: BlockPosition,
        sourceSide: EnumFacing?,
        sink: BlockPosition,
        sinkSide: EnumFacing?,
        limit: Int = 64
    ): (() -> Int)? {
        val sourceInventory = inventoryAt(source, sourceSide) ?: return null
        val sinkInventory = inventoryAt(sink, sinkSide) ?: return null
        return { transferBetweenInventories(sourceInventory, sinkInventory, limit) }
    }

    /**
     * Utility method for calling [transferBetweenInventoriesSlots] on inventories
     * in the world.
     */
    @JvmStatic
    @JvmOverloads
    fun getTransferBetweenInventoriesSlotsAt(
        sourcePos: BlockPosition,
        sourceSide: EnumFacing?,
        sourceSlot: Int,
        sinkPos: BlockPosition,
        sinkSide: EnumFacing?,
        sinkSlot: Int?,
        limit: Int = 64
    ): (() -> Int)? {
        val sourceInventory = inventoryAt(sourcePos, sourceSide) ?: return null
        val sinkInventory = inventoryAt(sinkPos, sinkSide) ?: return null
        return { transferBetweenInventoriesSlots(sourceInventory, sourceSlot, sinkInventory, sinkSlot, limit) }
    }

    /**
     * Utility method for dropping contents from a single inventory slot into
     * the world.
     */
    @JvmStatic
    @JvmOverloads
    fun dropSlot(
        position: BlockPosition,
        inventory: IInventory,
        slot: Int,
        count: Int,
        direction: EnumFacing? = null
    ): Boolean {
        val stack = inventory.decrStackSize(slot, count)
        return if (!stack.isEmpty && stack.count > 0) {
            spawnStackInWorld(position, stack, direction)
            true
        } else false
    }

    /**
     * Utility method for dumping all inventory contents into the world.
     */
    @JvmStatic
    fun dropAllSlots(position: BlockPosition, inventory: IInventory) {
        for (slot in 0 until inventory.sizeInventory) {
            val stack = inventory.getStackInSlot(slot)
            if (!stack.isEmpty && stack.count > 0) {
                inventory.setInventorySlotContents(slot, ItemStack.EMPTY)
                spawnStackInWorld(position, stack)
            }
        }
    }

    /**
     * Try inserting an item stack into a player inventory. If that fails, drop it into the world.
     */
    @JvmStatic
    @JvmOverloads
    fun addToPlayerInventory(stack: ItemStack, player: EntityPlayer, spawnInWorld: Boolean = true) {
        if (!stack.isEmpty) {
            if (player.inventory.addItemStackToInventory(stack)) {
                player.inventory.markDirty()
                player.openContainer?.detectAndSendChanges()
            }
            if (stack.count > 0 && spawnInWorld) {
                player.dropItem(stack, false, false)
            }
        }
    }

    /**
     * Utility method for spawning an item stack in the world.
     */
    @JvmStatic
    @JvmOverloads
    fun spawnStackInWorld(
        position: BlockPosition,
        stack: ItemStack,
        direction: EnumFacing? = null,
        validator: ((EntityItem) -> Boolean)? = null
    ): EntityItem? {
        val world = position.world ?: return null
        if (stack.isEmpty || stack.count <= 0) return null

        val rng = world.rand
        val (ox, oy, oz) = if (direction != null) {
            Triple(direction.xOffset, direction.yOffset, direction.zOffset)
        } else Triple(0, 0, 0)

        val tx = 0.1 * (rng.nextDouble() - 0.5) + ox * 0.65
        val ty = 0.1 * (rng.nextDouble() - 0.5) + oy * 0.75 + (ox + oz) * 0.25
        val tz = 0.1 * (rng.nextDouble() - 0.5) + oz * 0.65

        val dropPos = position.offset(0.5 + tx, 0.5 + ty, 0.5 + tz)
        val entity = EntityItem(world, dropPos.x, dropPos.y, dropPos.z, stack.copy())
        entity.motionX = 0.0125 * (rng.nextDouble() - 0.5) + ox * 0.03
        entity.motionY = 0.0125 * (rng.nextDouble() - 0.5) + oy * 0.08 + (ox + oz) * 0.03
        entity.motionZ = 0.0125 * (rng.nextDouble() - 0.5) + oz * 0.03

        return if (validator?.invoke(entity) != false) {
            entity.setPickupDelay(15)
            world.spawnEntity(entity)
            entity
        } else null
    }
}

sealed class InventorySource {
    abstract val side: EnumFacing?
    abstract val inventory: IItemHandler
}

data class BlockInventorySource(
    val position: BlockPosition,
    override val side: EnumFacing?,
    override val inventory: IItemHandler
) : InventorySource()

data class EntityInventorySource(
    val entity: Entity,
    override val side: EnumFacing?,
    override val inventory: IItemHandler
) : InventorySource()
