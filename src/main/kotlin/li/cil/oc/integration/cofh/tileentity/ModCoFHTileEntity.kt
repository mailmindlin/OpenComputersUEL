package li.cil.oc.integration.cofh.tileentity

import li.cil.oc.integration.Mod

import li.cil.oc.api.Driver
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModCoFHTileEntity : ModProxy {
    override val mod: Mod = Mods.CoFHCore

    override fun initialize() {
        Driver.add(DriverEnergyInfo())
        Driver.add(DriverRedstoneControl())
        Driver.add(DriverSecureTile())
        Driver.add(DriverSteamInfo())
    }
}
