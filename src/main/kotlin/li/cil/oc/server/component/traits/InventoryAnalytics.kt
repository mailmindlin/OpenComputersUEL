package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.ExtendedArguments.checkSlot
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.StackOption
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.OreDictionary

interface InventoryAnalytics : InventoryAware, NetworkAware {
    @Callback(doc = """function([slot:number]):table -- Get a description of the stack in the specified slot or the selected slot.""")
    fun getStackInInternalSlot(context: Context, args: Arguments): Array<Any?> {
        return if (Settings.get.allowItemStackInspection) {
            val slot = optSlot(args, 0)
            result(inventory.getStackInSlot(slot))
        } else {
            result(null, "not enabled in config")
        }
    }

    @Callback(doc = """function(otherSlot:number):boolean -- Get whether the stack in the selected slot is equivalent to the item in the specified slot (have shared OreDictionary IDs).""")
    fun isEquivalentTo(context: Context, args: Arguments): Array<Any?> {
        val slot = args.checkSlot(inventory, 0)

        val equivalent = when {
            stackInSlot(selectedSlot) is StackOption.SomeStack && stackInSlot(slot) is StackOption.SomeStack -> {
                val stackA = (stackInSlot(selectedSlot) as StackOption.SomeStack).stack
                val stackB = (stackInSlot(slot) as StackOption.SomeStack).stack
                OreDictionary.getOreIDs(stackA).intersect(OreDictionary.getOreIDs(stackB).toSet()).isNotEmpty()
            }
            stackInSlot(selectedSlot) is StackOption.EmptyStack && stackInSlot(slot) is StackOption.EmptyStack -> {
                true
            }
            else -> false
        }

        return result(equivalent)
    }

    @Callback(doc = """function(slot:number, dbAddress:string, dbSlot:number):boolean -- Store an item stack description in the specified slot of the database with the specified address.""")
    fun storeInternal(context: Context, args: Arguments): Array<Any?> {
        val localSlot = args.checkSlot(inventory, 0)
        val dbAddress = args.checkString(1)
        val localStack = inventory.getStackInSlot(localSlot)

        return DatabaseAccess.withDatabase(node, dbAddress) { database ->
            val dbSlot = args.checkSlot(database.data, 2)
            val nonEmpty = database.getStackInSlot(dbSlot) != ItemStack.EMPTY // zero size stacks!
            database.setStackInSlot(dbSlot, localStack.copy())
            result(nonEmpty)
        }
    }

    @Callback(doc = """function(slot:number, dbAddress:string, dbSlot:number[, checkNBT:boolean=false]):boolean -- Compare an item in the specified slot with one in the database with the specified address.""")
    fun compareToDatabase(context: Context, args: Arguments): Array<Any?> {
        val localSlot = args.checkSlot(inventory, 0)
        val dbAddress = args.checkString(1)
        val localStack = inventory.getStackInSlot(localSlot)

        return DatabaseAccess.withDatabase(node, dbAddress) { database ->
            val dbSlot = args.checkSlot(database.data, 2)
            val dbStack = database.getStackInSlot(dbSlot)
            result(InventoryUtils.haveSameItemType(localStack, dbStack, args.optBoolean(3, false)))
        }
    }
}
