package li.cil.oc.integration.ic2

import ic2.api.energy.tile.IEnergyConductor
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.integration.ManagedTileEntityEnvironment

internal class EnergyConductorEnvironment(tileEntity: IEnergyConductor) : ManagedTileEntityEnvironment<IEnergyConductor>(tileEntity, "energy_conductor") {
    @Callback
    fun getConductionLoss(context: Context?, args: Arguments?): Array<Any> {
        return arrayOf(tileEntity.conductionLoss)
    }

    @Callback
    fun getConductorBreakdownEnergy(context: Context?, args: Arguments?): Array<Any> {
        return arrayOf(tileEntity.conductorBreakdownEnergy)
    }

    @Callback
    fun getInsulationBreakdownEnergy(context: Context?, args: Arguments?): Array<Any> {
        return arrayOf(tileEntity.insulationBreakdownEnergy)
    }

    @Callback
    fun getInsulationEnergyAbsorption(context: Context?, args: Arguments?): Array<Any> {
        return arrayOf(tileEntity.insulationEnergyAbsorption)
    }
}
