package li.cil.oc.server.component.traits

import li.cil.oc.Settings
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.server.component.result
import li.cil.oc.util.FluidUtils
import li.cil.oc.util.optFluidCount
import net.minecraft.item.ItemStack
import net.minecraftforge.fluids.FluidStack

interface TankInventoryControl : WorldAware, InventoryAware, TankAware {
    @Callback(doc = """function([slot:number]):number -- Get the amount of fluid in the tank item in the specified slot or the selected slot.""")
    fun getTankLevelInSlot(context: Context, args: Arguments): Array<Any?> {
        return withFluidInfo(args.optSlot(0)) { fluid, _ ->
            result(fluid?.amount ?: 0)
        }
    }

    @Callback(doc = """function([slot:number]):number -- Get the capacity of the tank item in the specified slot of the robot or the selected slot.""")
    fun getTankCapacityInSlot(context: Context, args: Arguments): Array<Any?> {
        return withFluidInfo(args.optSlot(0)) { _, capacity ->
            result(capacity)
        }
    }

    @Callback(doc = """function([slot:number]):table -- Get a description of the fluid in the tank item in the specified slot or the selected slot.""")
    fun getFluidInTankInSlot(context: Context, args: Arguments): Array<Any?> {
        return if (Settings.get.allowItemStackInspection) {
            withFluidInfo(args.optSlot(0)) { fluid, _ ->
                result(fluid)
            }
        } else {
            result(null, "not enabled in config")
        }
    }

    @Callback(doc = """function([tank:number]):table -- Get a description of the fluid in the tank in the specified slot or the selected slot.""")
    fun getFluidInInternalTank(context: Context, args: Arguments): Array<Any?> {
        return if (Settings.get.allowItemStackInspection) {
            val fluid = tank.getFluidTank(optTank(args, 0))?.fluid
            result(fluid)
        } else {
            result(null, "not enabled in config")
        }
    }

    @Callback(doc = """function([amount:number]):boolean -- Transfers fluid from a tank in the selected inventory slot to the selected tank.""")
    fun drain(context: Context, args: Arguments): Array<Any?> {
        val amount = args.optFluidCount(0)
        val into = getTank(selectedTank)

        if (into != null) {
            val stack = inventory.getStackInSlot(selectedSlot)
            if (stack is ItemStack) {
                val handler = FluidUtils.fluidHandlerOf(stack)
                if (handler != null) {
                    val drained = handler.drain(amount, false)
                    val transferred = into.fill(drained, true)
                    if (transferred > 0) {
                        handler.drain(transferred, true)
                        inventory.setInventorySlotContents(selectedSlot, handler.container)
                        return result(true, transferred)
                    } else {
                        return result(null, "incompatible or no fluid")
                    }
                } else {
                    return result(null, "item is not a fluid container")
                }
            } else {
                return result(null, "nothing selected")
            }
        }

        return result(null, "no tank")
    }

    @Callback(doc = """function([amount:number]):boolean -- Transfers fluid from the selected tank to a tank in the selected inventory slot.""")
    fun fill(context: Context, args: Arguments): Array<Any?> {
        val amount = args.optFluidCount(0)
        val from = getTank(selectedTank)

        if (from != null) {
            val stack = inventory.getStackInSlot(selectedSlot)
            if (stack is ItemStack) {
                val handler = FluidUtils.fluidHandlerOf(stack)
                if (handler != null) {
                    val drained = from.drain(amount, false)
                    val transferred = handler.fill(drained, true)
                    if (transferred > 0) {
                        from.drain(transferred, true)
                        inventory.setInventorySlotContents(selectedSlot, handler.container)
                        return result(true, transferred)
                    } else {
                        return result(null, "incompatible or no fluid")
                    }
                } else {
                    return result(null, "item is not a fluid container")
                }
            } else {
                return result(null, "nothing selected")
            }
        }

        return result(null, "no tank")
    }

    fun withFluidInfo(slot: Int, f: (FluidStack?, Int) -> Array<Any?>): Array<Any?> {
        fun fluidInfo(stack: ItemStack): Pair<FluidStack?, Int>? {
            val handler = FluidUtils.fluidHandlerOf(stack)
            if (handler != null && handler.tankProperties.isNotEmpty()) {
                val props = handler.tankProperties[0]
                return Pair(props.contents, props.capacity)
            }
            return null
        }

        val stack = inventory.getStackInSlot(slot)
        if (stack is ItemStack) {
            val info = fluidInfo(stack)
            if (info != null) {
                return f(info.first, info.second)
            }
        }

        return result(null, "item is not a fluid container")
    }
}
