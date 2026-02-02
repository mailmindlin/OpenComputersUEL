package li.cil.oc.integration.cofh.tileentity

import li.cil.oc.integration.Mod

import li.cil.oc.api.Driver
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import li.cil.oc.integration.driverFor

internal object ModCoFHTileEntity : ModProxy {
    override val mod: Mod = Mods.CoFHCore

    override fun initialize() {
        Driver.add(DriverEnergyInfo())
        Driver.add(DriverRedstoneControl())
        Driver.add(driverFor(::EnvironmentSecureTile))
        Driver.add(driverFor(::EnvironmentSteamInfo))
    }
}
