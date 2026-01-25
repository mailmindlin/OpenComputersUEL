package li.cil.oc.server.component.traits

import li.cil.oc.api.internal.MultiTank
import li.cil.oc.api.machine.Arguments
import li.cil.oc.util.checkTank
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.IFluidTank

interface TankAware {
    val tank: MultiTank
    var selectedTank: Int

    // ----------------------------------------------------------------------- //

    fun optTank(args: Arguments, n: Int): Int {
        return if (args.count() > 0 && args.checkAny(0) != null) {
            args.checkTank(tank, 0)
        } else {
            selectedTank
        }
    }

    fun getTank(index: Int): IFluidTank? {
        return tank.getFluidTank(index)
    }

    fun fluidInTank(index: Int): FluidStack? {
        return getTank(index)?.fluid
    }

    fun haveSameFluidType(stackA: FluidStack, stackB: FluidStack): Boolean {
        return stackA.isFluidEqual(stackB)
    }
}
