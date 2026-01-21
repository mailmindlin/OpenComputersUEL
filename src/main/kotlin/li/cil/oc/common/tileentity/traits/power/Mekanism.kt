package li.cil.oc.common.tileentity.traits.power

/* TODO Mekanism

import cpw.mods.fml.common.Optional
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import net.minecraftforge.common.util.ForgeDirection

@Injectable.Interface(value = "mekanism.api.energy.IStrictEnergyAcceptor", modid = Mods.IDs.Mekanism)
interface Mekanism : Common {
    @Optional.Method(modid = Mods.IDs.Mekanism)
    fun canReceiveEnergy(side: ForgeDirection): Boolean = Mods.Mekanism.isAvailable && canConnectPower(side)

    @Optional.Method(modid = Mods.IDs.Mekanism)
    fun transferEnergyToAcceptor(side: ForgeDirection, amount: Double): Double =
        if (!Mods.Mekanism.isAvailable) 0.0
        else Power.toJoules(tryChangeBuffer(side, Power.fromJoules(amount)))

    @Optional.Method(modid = Mods.IDs.Mekanism)
    fun getMaxEnergy(): Double = Power.toJoules(ForgeDirection.VALID_DIRECTIONS.map { globalBufferSize(it) }.max() ?: 0.0)

    @Optional.Method(modid = Mods.IDs.Mekanism)
    fun getEnergy(): Double = Power.toJoules(ForgeDirection.VALID_DIRECTIONS.map { globalBuffer(it) }.max() ?: 0.0)

    @Optional.Method(modid = Mods.IDs.Mekanism)
    fun setEnergy(energy: Double) {}
}
*/

// Stub interface for when Mekanism is not available
interface Mekanism : Common
