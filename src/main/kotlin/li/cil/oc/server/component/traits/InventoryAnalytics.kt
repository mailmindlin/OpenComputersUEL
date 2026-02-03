package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.util.Result
import li.cil.oc.util.result
import li.cil.oc.util.DatabaseAccess
import li.cil.oc.util.InventoryUtils
import li.cil.oc.util.checkSlot
import net.minecraft.item.ItemStack
import net.minecraftforge.oredict.OreDictionary

interface InventoryAnalytics : InventoryAware, NetworkAware {
    @Callback(doc = """function([slot:number]):table -- Get a description of the stack in the specified slot or the selected slot.""")
    fun getStackInInternalSlot(context: Context, args: Arguments): Result {
        if (!Settings.get.allowItemStackInspection)
            return result(Unit, "not enabled in config")
        val slot = args.optSlot(0)
        return result(inventory.getStackInSlot(slot))
    }

    @Callback(doc = """function(otherSlot:number):boolean -- Get whether the stack in the selected slot is equivalent to the item in the specified slot (have shared OreDictionary IDs).""")
    fun isEquivalentTo(context: Context, args: Arguments): Result {
        val slot = args.checkSlot(inventory, 0)

        val stackA = stackInSlot(selectedSlot)
        val stackB = stackInSlot(slot)
        val equivalent = when {
            stackA != null && stackB != null -> OreDictionary.getOreIDs(stackA).intersect(OreDictionary.getOreIDs(stackB).toSet()).isNotEmpty()
            stackA == null && stackB == null -> true
            else -> false
        }

        return result(equivalent)
    }

    @Callback(doc = """function(slot:number, dbAddress:string, dbSlot:number):boolean -- Store an item stack description in the specified slot of the database with the specified address.""")
    fun storeInternal(context: Context, args: Arguments): Result {
        val localSlot = args.checkSlot(inventory, 0)
        val dbAddress = args.checkString(1)
        val localStack = inventory.getStackInSlot(localSlot)

        return DatabaseAccess.withDatabase(node!!, dbAddress) { database ->
            val dbSlot = args.checkSlot(database.data, 2)
            val nonEmpty = database.getStackInSlot(dbSlot) != ItemStack.EMPTY // zero size stacks!
            database.setStackInSlot(dbSlot, localStack.copy())
            result(nonEmpty)
        }
    }

    @Callback(doc = """function(slot:number, dbAddress:string, dbSlot:number[, checkNBT:boolean=false]):boolean -- Compare an item in the specified slot with one in the database with the specified address.""")
    fun compareToDatabase(context: Context, args: Arguments): Result {
        val localSlot = args.checkSlot(inventory, 0)
        val dbAddress = args.checkString(1)
        val localStack = inventory.getStackInSlot(localSlot)

        return DatabaseAccess.withDatabase(node!!, dbAddress) { database ->
            val dbSlot = args.checkSlot(database.data, 2)
            val dbStack = database.getStackInSlot(dbSlot)
            result(InventoryUtils.haveSameItemType(localStack, dbStack ?: ItemStack.EMPTY, args.optBoolean(3, false)))
        }
    }
}
