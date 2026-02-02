package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.prefab.ItemStackArrayValue
import li.cil.oc.server.component.result
import li.cil.oc.util.*
import net.minecraft.block.Block
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.common.registry.EntityRegistry
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.oredict.OreDictionary

interface WorldInventoryAnalytics : WorldAware, SideRestricted, NetworkAware {
    @Callback(doc = """function(side:number):number -- Get the number of slots in the inventory on the specified side of the device.""")
    fun getInventorySize(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        return withInventory(facing) { inventory ->
            result(inventory.slots)
        }
    }

    @Callback(doc = """function(side:number, slot:number):number -- Get number of items in the specified slot of the inventory on the specified side of the device.""")
    fun getSlotStackSize(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        return withInventory(facing) { inventory ->
            val slot = args.checkSlot(inventory, 1)
            val count = inventory.getStackInSlot(slot).notEmpty()?.count ?: 0
            result(count)
        }
    }

    @Callback(doc = """function(side:number, slot:number):number -- Get the maximum number of items in the specified slot of the inventory on the specified side of the device.""")
    fun getSlotMaxStackSize(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        return withInventory(facing) { inventory ->
            val slot = args.checkSlot(inventory, 1)
            val maxSize = inventory.getStackInSlot(slot).notEmpty()?.maxStackSize ?: 0
            result(maxSize)
        }
    }

    @Callback(doc = """function(side:number, slotA:number, slotB:number[, checkNBT:boolean=false]):boolean -- Get whether the items in the two specified slots of the inventory on the specified side of the device are of the same type.""")
    fun compareStacks(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        return withInventory(facing) { inventory ->
            val stackA = inventory.getStackInSlot(args.checkSlot(inventory, 1))
            val stackB = inventory.getStackInSlot(args.checkSlot(inventory, 2))
            val checkNBT = args.optBoolean(3, false)
            result(stackA == stackB || InventoryUtils.haveSameItemType(stackA, stackB, checkNBT))
        }
    }

    @Callback(doc = """function(side:number, slot:number, dbAddress:string, dbSlot:number[, checkNBT:boolean=false]):boolean -- Compare an item in the specified slot in the inventory on the specified side with one in the database with the specified address.""")
    fun compareStackToDatabase(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        return withInventory(facing) { inventory ->
            val slot = args.checkSlot(inventory, 1)
            val dbAddress = args.checkString(2)
            val stack = inventory.getStackInSlot(slot)

            DatabaseAccess.withDatabase(node!!, dbAddress) { database ->
                val dbSlot = args.checkSlot(database.data, 3)
                val dbStack = database.getStackInSlot(dbSlot)
                val checkNBT = args.optBoolean(4, false)
                result(InventoryUtils.haveSameItemType(stack, dbStack ?: ItemStack.EMPTY, checkNBT))
            }
        }
    }

    @Callback(doc = """function(side:number, slotA:number, slotB:number):boolean -- Get whether the items in the two specified slots of the inventory on the specified side of the device are equivalent (have shared OreDictionary IDs).""")
    fun areStacksEquivalent(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        return withInventory(facing) { inventory ->
            val stackA = inventory.getStackInSlot(args.checkSlot(inventory, 1))
            val stackB = inventory.getStackInSlot(args.checkSlot(inventory, 2))
            val equivalent = stackA == stackB ||
                (!stackA.isEmpty && !stackB.isEmpty &&
                 OreDictionary.getOreIDs(stackA).intersect(OreDictionary.getOreIDs(stackB).toSet()).isNotEmpty())
            result(equivalent)
        }
    }

    @Callback(doc = """function(side:number, slot:number):table -- Get a description of the stack in the inventory on the specified side of the device.""")
    fun getStackInSlot(context: Context, args: Arguments): Array<Any?> {
        return if (Settings.get.allowItemStackInspection) {
            val facing = checkSideForAction(args, 0)
            withInventory(facing) { inventory ->
                val slot = args.checkSlot(inventory, 1)
                result(inventory.getStackInSlot(slot))
            }
        } else {
            result(null, "not enabled in config")
        }
    }

    @Callback(doc = """function(side:number):userdata -- Get a description of all stacks in the inventory on the specified side of the device.""")
    fun getAllStacks(context: Context, args: Arguments): Array<Any?> {
        return if (Settings.get.allowItemStackInspection) {
            val facing = checkSideForAction(args, 0)
            withInventory(facing) { inventory ->
                val stacks = Array(inventory.slots) { i ->
                    inventory.getStackInSlot(i)
                }
                result(ItemStackArrayValue(stacks))
            }
        } else {
            result(null, "not enabled in config")
        }
    }

    @Callback(doc = """function(side:number):string -- Get the the name of the inventory on the specified side of the device.""")
    fun getInventoryName(context: Context, args: Arguments): Array<Any?> {
        return if (Settings.get.allowItemStackInspection) {
            val facing = checkSideForAction(args, 0)

            fun blockAt(position: BlockPosition): Block? {
                return position.world?.let { world ->
                    if (world.blockExists(position)) {
                        world.getBlock(position) as? Block
                    } else null
                }
            }

            withInventorySource(facing) { inventorySource ->
                when (inventorySource) {
                    is BlockInventorySource -> {
                        blockAt(inventorySource.position)?.let { block ->
                            result(block.registryName)
                        } ?: result(null, "Unknown")
                    }
                    is EntityInventorySource -> {
                        val entry = EntityRegistry.getEntry(inventorySource.entity.javaClass)
                        result(entry?.registryName)
                    }
                    else -> result(null, "Unknown")
                }
            }
        } else {
            result(null, "not enabled in config")
        }
    }

    @Callback(doc = """function(side:number, slot:number, dbAddress:string, dbSlot:number):boolean -- Store an item stack description in the specified slot of the database with the specified address.""")
    fun store(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        val dbAddress = args.checkString(2)

        fun store(stack: ItemStack): Array<Any?> {
            return DatabaseAccess.withDatabase(node!!, dbAddress) { database ->
                val dbSlot = args.checkSlot(database.data, 3)
                val nonEmpty = database.getStackInSlot(dbSlot) != ItemStack.EMPTY // zero size stacks
                database.setStackInSlot(dbSlot, stack.copy())
                result(nonEmpty)
            }
        }

        return withInventory(facing) { inventory ->
            val slot = args.checkSlot(inventory, 1)
            store(inventory.getStackInSlot(slot))
        }
    }

    fun withInventorySource(side: EnumFacing, f: (InventorySource) -> Array<Any?>): Array<Any?> {
        val inventorySource = InventoryUtils.inventorySourceAt(position.offset(side), side.opposite)
        return if (inventorySource != null && mayInteract(inventorySource)) {
            f(inventorySource)
        } else {
            result(null, "no inventory")
        }
    }

    fun withInventory(side: EnumFacing, f: (IItemHandler) -> Array<Any?>): Array<Any?> {
        return withInventorySource(side) { inventorySource ->
            f(inventorySource.inventory)
        }
    }
}
