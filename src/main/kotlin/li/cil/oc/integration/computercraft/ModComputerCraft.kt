package li.cil.oc.integration.computercraft

import li.cil.oc.integration.Mod

import li.cil.oc.api.Driver
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModComputerCraft : ModProxy {
    override val mod: Mod = Mods.ComputerCraft

    override fun initialize() {
        PeripheralProvider.init()

        Driver.add(DriverComputerCraftMedia)
        Driver.add(DriverPeripheral())

        Driver.add(ConverterLuaObject())
    }
}
