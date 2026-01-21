package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.ExtendedArguments.optTankProperties
import li.cil.oc.util.FluidUtils
import net.minecraftforge.fluids.capability.IFluidTankProperties

interface WorldTankAnalytics : WorldAware, SideRestricted {
    @Callback(doc = """function(side:number [, tank:number]):number -- Get the amount of fluid in the tank on the specified side.""")
    fun getTankLevel(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)

        val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite)
        return if (handler != null) {
            val properties = args.optTankProperties(handler, 1, null)
            if (properties is IFluidTankProperties) {
                result(properties.contents?.amount ?: 0)
            } else {
                val total = handler.tankProperties.sumBy { it.contents?.amount ?: 0 }
                result(total)
            }
        } else {
            result(null, "no tank")
        }
    }

    @Callback(doc = """function(side:number [, tank:number]):number -- Get the capacity of the tank on the specified side.""")
    fun getTankCapacity(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)

        val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite)
        return if (handler != null) {
            val properties = args.optTankProperties(handler, 1, null)
            if (properties is IFluidTankProperties) {
                result(properties.capacity)
            } else {
                val maxCapacity = handler.tankProperties.maxByOrNull { it.capacity }?.capacity ?: 0
                result(maxCapacity)
            }
        } else {
            result(null, "no tank")
        }
    }

    @Callback(doc = """function(side:number [, tank:number]):table -- Get a description of the fluid in the the tank on the specified side.""")
    fun getFluidInTank(context: Context, args: Arguments): Array<Any?> {
        return if (Settings.get.allowItemStackInspection) {
            val facing = checkSideForAction(args, 0)

            val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite)
            if (handler != null) {
                val properties = args.optTankProperties(handler, 1, null)
                if (properties is IFluidTankProperties) {
                    result(properties)
                } else {
                    result(handler.tankProperties)
                }
            } else {
                result(null, "no tank")
            }
        } else {
            result(null, "not enabled in config")
        }
    }

    @Callback(doc = """function(side:number):number -- Get the number of tanks available on the specified side.""")
    fun getTankCount(context: Context, args: Arguments): Array<Any?> {
        val facing = checkSideForAction(args, 0)

        val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite)
        return if (handler != null) {
            val properties = handler.tankProperties
            if (properties is Array<*>) {
                result(properties.size)
            } else {
                result(null, "no tank")
            }
        } else {
            result(null, "no tank")
        }
    }
}
