package li.cil.oc.integration.appeng

import appeng.api.AEApi
import appeng.api.implementations.items.IStorageCell
import appeng.api.storage.ICellInventory
import appeng.api.storage.ICellInventoryHandler
import appeng.api.storage.IMEInventoryHandler
import appeng.api.storage.channels.IItemStorageChannel
import appeng.api.storage.data.IAEItemStack
import li.cil.oc.api.driver.Converter
import net.minecraft.item.ItemStack

fun foo(): Nothing { TODO() }
class ConverterCellInventory : Converter {
    override fun convert(value: Any, output: MutableMap<Any, Any>) {
        if (value is ICellInventory<*>) {
            val cell = value as ICellInventory<IAEItemStack>
            output["storedItemTypes"] = cell.storedItemTypes
            output["storedItemCount"] = cell.storedItemCount
            output["remainingItemCount"] = cell.remainingItemCount
            output["remainingItemTypes"] = cell.remainingItemTypes

            output["getTotalItemTypes"] = cell.totalItemTypes
            output["getAvailableItems"] = cell.getAvailableItems(
                AEApi.instance().storage().getStorageChannel(
                    IItemStorageChannel::class.java
                ).createList()
            )

            output["totalBytes"] = cell.totalBytes
            output["freeBytes"] = cell.freeBytes
            output["usedBytes"] = cell.usedBytes
            output["unusedItemCount"] = cell.unusedItemCount
            output["canHoldNewItem"] = cell.canHoldNewItem()

            //output.put("getPreformattedItems",cell.getConfigInventory());
            output["fuzzyMode"] = cell.fuzzyMode.toString()
            output["name"] = (cell.itemStack as ItemStack).displayName
        } else if (value is ICellInventoryHandler<*>) {
            convert(value.cellInv!!, output)
        } else if ((value is ItemStack) && (value.item is IStorageCell<*>)) {
            val inventory: IMEInventoryHandler<*>? = AEApi.instance().registries().cell().getCellInventory(
                value, null, AEApi.instance().storage().getStorageChannel(
                    IItemStorageChannel::class.java
                )
            )
            if (inventory != null) convert((inventory as ICellInventoryHandler<*>).cellInv!!, output)
        }
    }
}
