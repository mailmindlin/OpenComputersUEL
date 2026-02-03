package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.util.Result
import li.cil.oc.util.result
import li.cil.oc.util.FluidUtils
import li.cil.oc.util.optTankProperties
import net.minecraftforge.fluids.capability.IFluidTankProperties

interface WorldTankAnalytics : WorldAware, SideRestricted {
    @Callback(doc = """function(side:number [, tank:number]):number -- Get the amount of fluid in the tank on the specified side.""")
    fun getTankLevel(context: Context, args: Arguments): Result {
        val facing = checkSideForAction(args, 0)

        val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite)
            ?: return result(Unit, "no tank")

        val properties = args.optTankProperties(handler, 1)
        return if (properties is IFluidTankProperties) {
            result(properties.contents?.amount ?: 0)
        } else {
            val total = handler.tankProperties.sumOf { it.contents?.amount ?: 0 }
            result(total)
        }
    }

    @Callback(doc = """function(side:number [, tank:number]):number -- Get the capacity of the tank on the specified side.""")
    fun getTankCapacity(context: Context, args: Arguments): Result {
        val facing = checkSideForAction(args, 0)

        val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite)
            ?: return result(Unit, "no tank")
        val properties = args.optTankProperties(handler, 1)
        return if (properties is IFluidTankProperties) {
            result(properties.capacity)
        } else {
            val maxCapacity = handler.tankProperties.maxOfOrNull { it.capacity } ?: 0
            result(maxCapacity)
        }
    }

    @Callback(doc = """function(side:number [, tank:number]):table -- Get a description of the fluid in the the tank on the specified side.""")
    fun getFluidInTank(context: Context, args: Arguments): Result {
        if (!Settings.get.allowItemStackInspection)
            return result(Unit, "not enabled in config")
        val facing = checkSideForAction(args, 0)

        val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite) ?: return result(Unit, "no tank")
        val properties = args.optTankProperties(handler, 1)
        return result(
            if (properties is IFluidTankProperties) properties
            else handler.tankProperties
        )
    }

    @Callback(doc = """function(side:number):number -- Get the number of tanks available on the specified side.""")
    fun getTankCount(context: Context, args: Arguments): Result {
        val facing = checkSideForAction(args, 0)

        val handler = FluidUtils.fluidHandlerAt(position.offset(facing), facing.opposite) ?: return result(Unit, "no tank")
        val properties = handler.tankProperties
            ?.takeIf { it.isNotEmpty() }
            ?: return result(Unit, "no tank")
        return result(properties.size)
    }
}
