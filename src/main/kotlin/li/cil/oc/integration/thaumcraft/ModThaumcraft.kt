package li.cil.oc.integration.thaumcraft

import li.cil.oc.integration.Mod

import li.cil.oc.api.Driver
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModThaumcraft : ModProxy {
    override val mod: Mod = Mods.Thaumcraft

    override fun initialize() {
        Driver.add(ConverterThaumcraftItems)
    }
}
