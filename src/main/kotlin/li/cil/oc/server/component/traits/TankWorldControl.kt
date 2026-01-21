package li.cil.oc.server.component.traits

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.ExtendedArguments.optFluidCount
import li.cil.oc.util.ExtendedArguments.optTankProperties
import li.cil.oc.util.FluidUtils
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidTankProperties

interface TankWorldControl : TankAware, WorldAware, SideRestricted {
    @Callback(doc = "function(side:number [, tank:number]):boolean -- Compare the fluid in the selected tank with the fluid in the specified tank on the specified side. Returns true if equal.")
    fun compareFluid(context: Context, args: Arguments): Array<Any?> {
        val side = checkSideForAction(args, 0)
        val stack = fluidInTank(selectedTank)

        if (stack != null) {
            val handler = FluidUtils.fluidHandlerAt(position.offset(side), side.opposite)
            if (handler != null) {
                val properties = args.optTankProperties(handler, 1, null)
                return if (properties is IFluidTankProperties) {
                    result(stack.isFluidEqual(properties.contents))
                } else {
                    val hasMatch = handler.tankProperties.any { other ->
                        stack.isFluidEqual(other.contents)
                    }
                    result(hasMatch)
                }
            } else {
                return result(false)
            }
        }

        return result(false)
    }

    @Callback(doc = "function(side:boolean[, amount:number=1000]):boolean, number or string -- Drains the specified amount of fluid from the specified side. Returns the amount drained, or an error message.")
    fun drain(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        val count = maxOf(args.optFluidCount(1), 0)
        val selectedTankInstance = getTank(selectedTank)

        if (selectedTankInstance != null) {
            val space = selectedTankInstance.capacity - selectedTankInstance.fluidAmount
            val amount = minOf(count, space)

            if (count < 1 || amount > 0) {
                val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite)
                if (handler != null) {
                    val existingFluid = selectedTankInstance.fluid
                    return if (existingFluid is FluidStack) {
                        val drained = handler.drain(FluidStack(existingFluid, amount), true)
                        if ((drained != null && drained.amount > 0) || amount == 0) {
                            val filled = selectedTankInstance.fill(drained, true)
                            result(true, filled)
                        } else {
                            result(null, "incompatible or no fluid")
                        }
                    } else {
                        val transferred = selectedTankInstance.fill(handler.drain(amount, true), true)
                        result(transferred > 0, transferred)
                    }
                } else {
                    return result(null, "incompatible or no fluid")
                }
            } else {
                return result(null, "tank is full")
            }
        }

        return result(null, "no tank selected")
    }

    @Callback(doc = "function(side:number[, amount:number=1000]):boolean, number of string -- Eject the specified amount of fluid to the specified side. Returns the amount ejected or an error message.")
    fun fill(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)
        val count = maxOf(args.optFluidCount(1), 0)
        val selectedTankInstance = getTank(selectedTank)

        if (selectedTankInstance != null) {
            val amount = minOf(count, selectedTankInstance.fluidAmount)

            if (count < 1 || amount > 0) {
                val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite)
                if (handler != null) {
                    val existingFluid = selectedTankInstance.fluid
                    return if (existingFluid is FluidStack) {
                        val filled = handler.fill(FluidStack(existingFluid, amount), true)
                        if (filled > 0 || amount == 0) {
                            selectedTankInstance.drain(filled, true)
                            result(true, filled)
                        } else {
                            result(null, "incompatible or no fluid")
                        }
                    } else {
                        result(null, "tank is empty")
                    }
                } else {
                    return result(null, "no space")
                }
            } else {
                return result(null, "tank is empty")
            }
        }

        return result(null, "no tank selected")
    }
}
