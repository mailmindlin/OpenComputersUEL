package li.cil.oc.integration.mekanism.gas

import li.cil.oc.api.Driver
import li.cil.oc.integration.Mod
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModMekanismGas : ModProxy {
    override val mod: Mod = Mods.MekanismGas

    override fun initialize() {
        Driver.add(ConverterGasStack)
    }
}
