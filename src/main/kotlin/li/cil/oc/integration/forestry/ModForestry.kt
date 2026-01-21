package li.cil.oc.integration.forestry

import li.cil.oc.integration.Mod

import li.cil.oc.api.Driver
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModForestry : ModProxy {
    override val mod: Mod = Mods.Forestry

    override fun initialize() {
        Driver.add(ConverterIAlleles())
        Driver.add(ConverterIIndividual())
        Driver.add(ConverterItemStack)
        Driver.add(DriverAnalyzer())
        Driver.add(DriverBeeHouse())
    }
}
