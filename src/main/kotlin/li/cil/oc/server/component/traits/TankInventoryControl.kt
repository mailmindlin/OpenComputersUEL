package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.util.*
import li.cil.oc.util.Result
import net.minecraft.item.ItemStack
import net.minecraftforge.fluids.FluidStack

interface TankInventoryControl : WorldAware, InventoryAware, TankAware {
    @Callback(doc = """function([slot:number]):number -- Get the amount of fluid in the tank item in the specified slot or the selected slot.""")
    fun getTankLevelInSlot(context: Context, args: Arguments): Result {
        return withFluidInfo(args.optSlot(0)) { fluid, _ ->
            result(fluid?.amount ?: 0)
        }
    }

    @Callback(doc = """function([slot:number]):number -- Get the capacity of the tank item in the specified slot of the robot or the selected slot.""")
    fun getTankCapacityInSlot(context: Context, args: Arguments): Result {
        return withFluidInfo(args.optSlot(0)) { _, capacity ->
            result(capacity)
        }
    }

    @Callback(doc = """function([slot:number]):table -- Get a description of the fluid in the tank item in the specified slot or the selected slot.""")
    fun getFluidInTankInSlot(context: Context, args: Arguments): Result {
        if (!Settings.get.allowItemStackInspection)
            return result(Unit, "not enabled in config")

        return withFluidInfo(args.optSlot(0)) { fluid, _ -> result(fluid) }
    }

    @Callback(doc = """function([tank:number]):table -- Get a description of the fluid in the tank in the specified slot or the selected slot.""")
    fun getFluidInInternalTank(context: Context, args: Arguments): Result {
        if (!Settings.get.allowItemStackInspection)
            return result(Unit, "not enabled in config")
        val fluid = tank.getFluidTank(optTank(args, 0))?.fluid
        return result(fluid)
    }

    @Callback(doc = """function([amount:number]):boolean -- Transfers fluid from a tank in the selected inventory slot to the selected tank.""")
    fun drain(context: Context, args: Arguments): Result {
        val amount = args.optFluidCount(0)
        val into = getTank(selectedTank) ?: return result(Unit, "no tank")

        val stack = inventory.getStackInSlot(selectedSlot) ?: return result(Unit, "nothing selected")
        val handler = FluidUtils.fluidHandlerOf(stack) ?: return result(Unit, "item is not a fluid container")
        val drained = handler.drain(amount, false)
        val transferred = into.fill(drained, true)
        if (transferred <= 0)
            return result(Unit, "incompatible or no fluid")
        handler.drain(transferred, true)
        inventory.setInventorySlotContents(selectedSlot, handler.container)
        return result(true, transferred)
    }

    @Callback(doc = """function([amount:number]):boolean -- Transfers fluid from the selected tank to a tank in the selected inventory slot.""")
    fun fill(context: Context, args: Arguments): Result {
        val amount = args.optFluidCount(0)
        val from = getTank(selectedTank) ?: return result(Unit, "no tank")

        val stack = inventory.getStackInSlot(selectedSlot) ?: return result(Unit, "nothing selected")
        val handler = FluidUtils.fluidHandlerOf(stack) ?: return result(Unit, "item is not a fluid container")
        val drained = from.drain(amount, false)
        val transferred = handler.fill(drained, true)
        if (transferred <= 0)
            return result(Unit, "incompatible or no fluid")
        from.drain(transferred, true)
        inventory.setInventorySlotContents(selectedSlot, handler.container)
        return result(true, transferred)
    }

    fun withFluidInfo(slot: Int, f: (FluidStack?, Int) -> Result): Result {
        fun fluidInfo(stack: ItemStack): Pair<FluidStack?, Int>? {
            val props = FluidUtils.fluidHandlerOf(stack)?.tankProperties?.firstOrNull() ?: return null
            return Pair(props.contents, props.capacity)
        }

        val (stack, capacity) = inventory.getStackInSlot(slot)
            ?.let(::fluidInfo)
            ?: return result(Unit, "item is not a fluid container")
        return f(stack, capacity)
    }
}
