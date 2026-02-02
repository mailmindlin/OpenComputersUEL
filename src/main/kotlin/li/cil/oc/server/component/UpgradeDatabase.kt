package li.cil.oc.server.component

import com.google.common.hash.Hashing
import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Database
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.*
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack

class UpgradeDatabase(val data: IInventory) : ManagedEnvironmentKt(), Database, DeviceInfo {
    override val node = nodeFactory(Visibility.Network, "database").create()

    private val deviceInfo_ by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Generic,
            DeviceAttribute.Description to "Object catalogue",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "iCatalogue (patent pending)",
            DeviceAttribute.Capacity to size().toString()
        )
    }

    override fun getDeviceInfo() = deviceInfo_

    override fun size(): Int = data.sizeInventory

    override fun getStackInSlot(slot: Int): ItemStack? = data.getStackInSlot(slot)?.notEmpty()?.copy()

    override fun setStackInSlot(slot: Int, stack: ItemStack?) {
        data.setInventorySlotContents(slot, stack)
    }

    override fun findStackWithHash(needle: String): Int = indexOf(needle)

    @Suppress("unused", "unused_parameter")
    @Callback(doc = "function(slot:number):table -- Get the representation of the item stack stored in the specified slot.")
    fun get(context: Context, args: Arguments): Result
        = result(data.getStackInSlot(args.checkSlot(data, 0)))

    @Suppress("unused", "unused_parameter")
    @Callback(doc = "function(slot:number):string -- Computes a hash value for the item stack in the specified slot.")
    fun computeHash(context: Context, args: Arguments): Result? {
        val stack = data.getStackInSlot(args.checkSlot(data, 0))
        return if (stack is ItemStack) {
            val hash = Hashing.sha256().hashBytes(ItemUtils.saveStack(stack))
            result(hash.toString())
        } else {
            null
        }
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = "function(hash:string):number -- Get the index of an item stack with the specified hash. Returns a negative value if no such stack was found.")
    fun indexOf(context: Context, args: Arguments): Result = result(indexOf(args.checkString(0), 1))

    @Suppress("unused", "unused_parameter")
    @Callback(doc = "function(slot:number):boolean -- Clears the specified slot. Returns true if there was something in the slot before.")
    fun clear(context: Context, args: Arguments): Result {
        val slot = args.checkSlot(data, 0)
        val nonEmpty = data.getStackInSlot(slot) != ItemStack.EMPTY // zero size stacks
        data.setInventorySlotContents(slot, ItemStack.EMPTY)
        return result(nonEmpty)
    }

    @Suppress("unused", "unused_parameter")
    @Callback(doc = "function(fromSlot:number, toSlot:number[, address:string]):boolean -- Copies an entry to another slot, optionally to another database. Returns true if something was overwritten.")
    fun copy(context: Context, args: Arguments): Result {
        val fromSlot = args.checkSlot(data, 0)
        val entry = data.getStackInSlot(fromSlot)

        fun set(inventory: IInventory): Result {
            val toSlot = args.checkSlot(inventory, 1)
            val nonEmpty = inventory.getStackInSlot(toSlot) != ItemStack.EMPTY // zero size stacks
            inventory.setInventorySlotContents(toSlot, entry.copy())
            return result(nonEmpty)
        }

        return if (args.count() > 2) {
            DatabaseAccess.withDatabase(node!!, args.checkString(2)) { database ->
                set(database.data)
            }
        } else {
            set(data)
        }
    }

    @Callback(doc = "function(address:string):number -- Copies the data stored in this database to another database with the specified address.")
    fun clone(context: Context, args: Arguments): Result {
        return DatabaseAccess.withDatabase(node!!, args.checkString(0)) { database ->
            val numberToCopy = minOf(data.sizeInventory, database.data.sizeInventory)
            for (slot in 0 until numberToCopy) {
                database.data.setInventorySlotContents(slot, data.getStackInSlot(slot).copy())
            }
            context.pause(0.25)
            result(numberToCopy)
        }
    }

    private fun indexOf(needle: String, offset: Int = 0): Int {
        for (slot in 0 until data.sizeInventory) {
            val stack = data.getStackInSlot(slot)
            if (stack is ItemStack) {
                val hash = Hashing.sha256().hashBytes(ItemUtils.saveStack(stack))
                if (hash.toString() == needle) {
                    return slot + offset
                }
            }
        }
        return -1
    }
}
