package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.util.Result
import li.cil.oc.util.result
import li.cil.oc.util.FluidUtils
import li.cil.oc.util.optFluidCount
import li.cil.oc.util.optTankProperties
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidTankProperties

interface TankWorldControl : TankAware, WorldAware, SideRestricted {
    @Callback(doc = "function(side:number [, tank:number]):boolean -- Compare the fluid in the selected tank with the fluid in the specified tank on the specified side. Returns true if equal.")
    fun compareFluid(context: Context, args: Arguments): Result {
        val side = checkSideForAction(args, 0)
        val stack = fluidInTank(selectedTank) ?: return result(false)

        val handler = FluidUtils.fluidHandlerAt(position.offset(side), side.opposite) ?: return result(false)
        val properties = args.optTankProperties(handler, 1)
        return result(if (properties is IFluidTankProperties) {
            stack.isFluidEqual(properties.contents)
        } else {
            handler.tankProperties.any { stack.isFluidEqual(it.contents) }
        })
    }

    @Callback(doc = "function(side:boolean[, amount:number=1000]):boolean, number or string -- Drains the specified amount of fluid from the specified side. Returns the amount drained, or an error message.")
    fun drain(context: Context, args: Arguments): Result {
        val facing = checkSideForAction(args, 0)
        val count = maxOf(args.optFluidCount(1), 0)
        val selectedTankInstance = getTank(selectedTank) ?: return result(Unit, "no tank selected")

        val space = selectedTankInstance.capacity - selectedTankInstance.fluidAmount
        val amount = minOf(count, space)

        if (count >= 1 && amount <= 0)
            return result(Unit, "tank is full")
        val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite) ?: return result(Unit, "incompatible or no fluid")
        val existingFluid = selectedTankInstance.fluid

        return if (existingFluid is FluidStack) {
            val drained = handler.drain(FluidStack(existingFluid, amount), true)
            if ((drained != null && drained.amount > 0) || amount == 0) {
                val filled = selectedTankInstance.fill(drained, true)
                result(true, filled)
            } else {
                result(Unit, "incompatible or no fluid")
            }
        } else {
            val transferred = selectedTankInstance.fill(handler.drain(amount, true), true)
            result(transferred > 0, transferred)
        }
    }

    @Callback(doc = "function(side:number[, amount:number=1000]):boolean, number of string -- Eject the specified amount of fluid to the specified side. Returns the amount ejected or an error message.")
    fun fill(context: Context, args: Arguments): Result {
        val facing = checkSideForAction(args, 0)
        val count = maxOf(args.optFluidCount(1), 0)
        val selectedTankInstance = getTank(selectedTank) ?: return result(Unit, "no tank selected")

        val amount = minOf(count, selectedTankInstance.fluidAmount)

        if (count >= 1 && amount <= 0)
            return result(Unit, "tank is empty")
        val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite) ?: return result(Unit, "no space")
        val existingFluid = selectedTankInstance.fluid ?: return result(Unit, "tank is empty")

        val filled = handler.fill(FluidStack(existingFluid, amount), true)
        return if (filled > 0 || amount == 0) {
            selectedTankInstance.drain(filled, true)
            result(true, filled)
        } else {
            result(Unit, "incompatible or no fluid")
        }
    }
}
