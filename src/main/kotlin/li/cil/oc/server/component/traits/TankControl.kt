package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.ExtendedArguments.checkTank
import li.cil.oc.util.ExtendedArguments.optFluidCount

interface TankControl : TankAware {
    @Callback(doc = "function():number -- The number of tanks installed in the device.")
    fun tankCount(context: Context, args: Arguments): Array<Any?> {
        return result(tank.tankCount())
    }

    @Callback(doc = "function([index:number]):number -- Select a tank and/or get the number of the currently selected tank.")
    fun selectTank(context: Context, args: Arguments): Array<Any?> {
        if (args.count() > 0 && args.checkAny(0) != null) {
            selectedTank = args.checkTank(tank, 0)
        }
        return result(selectedTank + 1)
    }

    @Callback(direct = true, doc = "function([index:number]):number -- Get the fluid amount in the specified or selected tank.")
    fun tankLevel(context: Context, args: Arguments): Array<Any?> {
        val index = if (args.count() > 0 && args.checkAny(0) != null) {
            args.checkTank(tank, 0)
        } else {
            selectedTank
        }

        val amount = fluidInTank(index)?.amount ?: 0
        return result(amount)
    }

    @Callback(direct = true, doc = "function([index:number]):number -- Get the remaining fluid capacity in the specified or selected tank.")
    fun tankSpace(context: Context, args: Arguments): Array<Any?> {
        val index = if (args.count() > 0 && args.checkAny(0) != null) {
            args.checkTank(tank, 0)
        } else {
            selectedTank
        }

        val space = getTank(index)?.let { it.capacity - it.fluidAmount } ?: 0
        return result(space)
    }

    @Callback(doc = "function(index:number):boolean -- Compares the fluids in the selected and the specified tank. Returns true if equal.")
    fun compareFluidTo(context: Context, args: Arguments): Array<Any?> {
        val index = args.checkTank(tank, 0)

        val equal = when {
            fluidInTank(selectedTank) != null && fluidInTank(index) != null -> {
                haveSameFluidType(fluidInTank(selectedTank)!!, fluidInTank(index)!!)
            }
            fluidInTank(selectedTank) == null && fluidInTank(index) == null -> {
                true
            }
            else -> false
        }

        return result(equal)
    }

    @Callback(doc = "function(index:number[, count:number=1000]):boolean -- Move the specified amount of fluid from the selected tank into the specified tank.")
    fun transferFluidTo(context: Context, args: Arguments): Array<Any?> {
        val index = args.checkTank(tank, 0)
        val count = args.optFluidCount(1)

        if (index == selectedTank || count == 0) {
            return result(true)
        }

        val fromTank = getTank(selectedTank)
        val toTank = getTank(index)

        if (fromTank != null && toTank != null) {
            val drained = fromTank.drain(count, false)
            val transferred = toTank.fill(drained, true)

            return if (transferred > 0) {
                fromTank.drain(transferred, true)
                result(true)
            } else if (count >= fromTank.fluidAmount &&
                       toTank.capacity >= fromTank.fluidAmount &&
                       fromTank.capacity >= toTank.fluidAmount) {
                // Swap.
                val tmp = toTank.drain(toTank.fluidAmount, true)
                toTank.fill(fromTank.drain(fromTank.fluidAmount, true), true)
                fromTank.fill(tmp, true)
                result(true)
            } else {
                result(null, "incompatible or no fluid")
            }
        }

        return result(null, "invalid index")
    }
}
