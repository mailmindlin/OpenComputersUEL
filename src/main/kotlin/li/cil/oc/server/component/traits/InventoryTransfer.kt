package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.ExtendedArguments.checkSlot
import li.cil.oc.util.ExtendedArguments.optItemCount
import li.cil.oc.util.ExtendedArguments.optFluidCount
import li.cil.oc.util.ExtendedArguments.optSlot
import li.cil.oc.util.FluidUtils
import li.cil.oc.util.InventoryUtils

interface InventoryTransfer : WorldAware, SideRestricted {
    // Return null on success, else failure reason string
    fun onTransferContents(): String?

    @Callback(doc = """function(sourceSide:number, sinkSide:number[, count:number[, sourceSlot:number[, sinkSlot:number]]]):boolean -- Transfer some items between two inventories.""")
    fun transferItem(context: Context, args: Arguments): Array<Any?> {
        val sourceSide = checkSideForAction(args, 0)
        val sourcePos = position.offset(sourceSide)
        val sinkSide = checkSideForAction(args, 1)
        val sinkPos = position.offset(sinkSide)
        val count = args.optItemCount(2)

        onTransferContents()?.let { reason ->
            return result(null, reason)
        }

        val extractor = if (args.count() > 3) {
            val sourceInventory = InventoryUtils.inventoryAt(sourcePos, sourceSide.opposite)
                ?: throw IllegalArgumentException("no inventory")
            val sourceSlot = args.checkSlot(sourceInventory, 3)

            val sinkInventory = InventoryUtils.inventoryAt(sinkPos, sinkSide.opposite)
                ?: throw IllegalArgumentException("no inventory")
            val sinkSlot = args.optSlot(sinkInventory, 4, -1)

            InventoryUtils.getTransferBetweenInventoriesSlotsAt(
                sourcePos,
                sourceSide.opposite,
                sourceSlot,
                sinkPos,
                sinkSide.opposite,
                if (sinkSlot < 0) null else sinkSlot,
                count
            )
        } else {
            InventoryUtils.getTransferBetweenInventoriesAt(
                sourcePos,
                sourceSide.opposite,
                sinkPos,
                sinkSide.opposite,
                count
            )
        }

        return if (extractor != null) {
            result(extractor())
        } else {
            result(null, "no inventory")
        }
    }

    @Callback(doc = """function(sourceSide:number, sinkSide:number[, count:number [, sourceTank:number]]):boolean, number -- Transfer some fluid between two tanks. Returns operation result and filled amount""")
    fun transferFluid(context: Context, args: Arguments): Array<Any?> {
        val sourceSide = checkSideForAction(args, 0)
        val sourcePos = position.offset(sourceSide)
        val sinkSide = checkSideForAction(args, 1)
        val sinkPos = position.offset(sinkSide)
        val count = args.optFluidCount(2)
        val sourceTank = args.optInteger(3, -1)

        onTransferContents()?.let { reason ->
            return result(null, reason)
        }

        val moved = FluidUtils.transferBetweenFluidHandlersAt(
            sourcePos,
            sourceSide.opposite,
            sinkPos,
            sinkSide.opposite,
            count,
            sourceTank
        )

        if (moved > 0) {
            // Allow up to 16 buckets per second.
            context.pause(moved.toDouble() / Settings.get.transposerFluidTransferRate)
        }

        return result(moved > 0, moved)
    }
}
