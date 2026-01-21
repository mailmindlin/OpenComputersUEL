package li.cil.oc.util

import li.cil.oc.api.internal.MultiTank
import li.cil.oc.api.machine.Arguments
import net.minecraft.inventory.IInventory
import net.minecraft.util.EnumFacing
import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.fluids.capability.IFluidTankProperties
import net.minecraftforge.items.IItemHandler
import kotlin.math.max
import kotlin.math.min

fun Arguments.optItemCount(index: Int, default: Int = 64): Int =
    if (!isDefined(index) || !hasValue(index)) default
    else max(0, min(64, checkInteger(index)))

fun Arguments.optFluidCount(index: Int, default: Int = Fluid.BUCKET_VOLUME): Int =
    if (!isDefined(index) || !hasValue(index)) default
    else max(0, checkInteger(index))

fun Arguments.checkSlot(inventory: IItemHandler, n: Int): Int {
    val slot = checkInteger(n) - 1
    if (slot < 0 || slot >= inventory.slots) {
        throw IllegalArgumentException("invalid slot")
    }
    return slot
}

fun Arguments.optSlot(inventory: IItemHandler, index: Int, default: Int): Int =
    if (!isDefined(index)) default
    else checkSlot(inventory, index)

fun Arguments.checkSlot(inventory: IInventory, n: Int): Int =
    checkSlot(InventoryUtils.asItemHandler(inventory), n)

fun Arguments.optSlot(inventory: IInventory, index: Int, default: Int): Int =
    optSlot(InventoryUtils.asItemHandler(inventory), index, default)

fun Arguments.checkTank(multi: MultiTank, n: Int): Int {
    val tank = checkInteger(n) - 1
    if (tank < 0 || tank >= multi.tankCount()) {
        throw IllegalArgumentException("invalid tank index")
    }
    return tank
}

fun Arguments.checkTankProperties(handler: IFluidHandler, n: Int): IFluidTankProperties {
    val tank = checkInteger(n) - 1
    val tankInfo = handler.tankProperties
    if (tankInfo == null || tank < 0 || tank >= tankInfo.size) {
        throw IllegalArgumentException("invalid tank index")
    }
    return tankInfo[tank]
}

fun Arguments.optTankProperties(handler: IFluidHandler, n: Int, default: IFluidTankProperties): IFluidTankProperties =
    if (!isDefined(n)) default
    else checkTankProperties(handler, n)

fun Arguments.checkSideAny(index: Int): EnumFacing =
    checkSide(index, *EnumFacing.values())

fun Arguments.optSideAny(index: Int, default: EnumFacing): EnumFacing =
    if (!isDefined(index)) default
    else checkSideAny(index)

fun Arguments.checkSideExcept(index: Int, vararg invalid: EnumFacing): EnumFacing =
    checkSide(index, *EnumFacing.values().filter { it !in invalid }.toTypedArray())

fun Arguments.optSideExcept(index: Int, default: EnumFacing, vararg invalid: EnumFacing): EnumFacing =
    if (!isDefined(index)) default
    else checkSideExcept(index, *invalid)

fun Arguments.checkSideForAction(index: Int): EnumFacing =
    checkSide(index, EnumFacing.SOUTH, EnumFacing.UP, EnumFacing.DOWN)

fun Arguments.optSideForAction(index: Int, default: EnumFacing): EnumFacing =
    if (!isDefined(index)) default
    else checkSideForAction(index)

fun Arguments.checkSideForMovement(index: Int): EnumFacing =
    checkSide(index, EnumFacing.SOUTH, EnumFacing.NORTH, EnumFacing.UP, EnumFacing.DOWN)

fun Arguments.optSideForMovement(index: Int, default: EnumFacing): EnumFacing =
    if (!isDefined(index)) default
    else checkSideForMovement(index)

fun Arguments.checkSideForFace(index: Int, facing: EnumFacing): EnumFacing =
    checkSideExcept(index, facing.opposite)

fun Arguments.optSideForFace(index: Int, default: EnumFacing): EnumFacing =
    if (!isDefined(index)) default
    else checkSideForAction(index)

private fun Arguments.checkSide(index: Int, vararg allowed: EnumFacing): EnumFacing {
    val side = checkInteger(index)
    if (side < 0 || side > 5) {
        throw IllegalArgumentException("invalid side")
    }
    val direction = EnumFacing.byIndex(side)
    return if (allowed.isEmpty() || direction in allowed) direction
    else throw IllegalArgumentException("unsupported side")
}

private fun Arguments.isDefined(index: Int): Boolean = index >= 0 && index < count()

private fun Arguments.hasValue(index: Int): Boolean = checkAny(index) != null
