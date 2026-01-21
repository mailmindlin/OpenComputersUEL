package li.cil.oc.common.tileentity.traits.power

/* TODO Galacticraft

import cpw.mods.fml.common.Optional
import li.cil.oc.common.asm.Injectable
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.Power
import micdoodle8.mods.galacticraft.api.power.EnergySource
import micdoodle8.mods.galacticraft.api.transmission.NetworkType
import net.minecraftforge.common.util.ForgeDirection

import scala.language.implicitConversions

@Injectable.InterfaceList(Array(
  new Injectable.Interface(value = "micdoodle8.mods.galacticraft.api.power.IEnergyHandlerGC", modid = Mods.IDs.Galacticraft),
  new Injectable.Interface(value = "micdoodle8.mods.galacticraft.api.transmission.tile.IConnector", modid = Mods.IDs.Galacticraft)
))
interface Galacticraft : Common {

    @Optional.Method(modid = Mods.IDs.Galacticraft)
    fun nodeAvailable(from: EnergySource): Boolean = Mods.Galacticraft.isAvailable && canConnectPower(from.toDirection())

    @Optional.Method(modid = Mods.IDs.Galacticraft)
    fun receiveEnergyGC(from: EnergySource, amount: Float, simulate: Boolean): Float =
        if (!Mods.Galacticraft.isAvailable) 0f
        else Power.toGC(tryChangeBuffer(from.toDirection(), Power.fromGC(amount), !simulate))

    @Optional.Method(modid = Mods.IDs.Galacticraft)
    fun getEnergyStoredGC(from: EnergySource): Float = Power.toGC(globalBuffer(from.toDirection()))

    @Optional.Method(modid = Mods.IDs.Galacticraft)
    fun getMaxEnergyStoredGC(from: EnergySource): Float = Power.toGC(globalBufferSize(from.toDirection()))

    @Optional.Method(modid = Mods.IDs.Galacticraft)
    fun extractEnergyGC(from: EnergySource, amount: Float, simulate: Boolean): Float = 0f

    @Optional.Method(modid = Mods.IDs.Galacticraft)
    fun canConnect(from: ForgeDirection, networkType: NetworkType): Boolean =
        networkType == NetworkType.POWER && canConnectPower(from)
}
*/

// Stub interface for when Galacticraft is not available
interface Galacticraft : Common
