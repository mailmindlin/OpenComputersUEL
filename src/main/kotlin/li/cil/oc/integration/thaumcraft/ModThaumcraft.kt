package li.cil.oc.integration.thaumcraft

import li.cil.oc.api.Driver
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

object ModThaumcraft : ModProxy() {
    override fun getMod(): Mods.ModBase = Mods.Thaumcraft

    override fun initialize() {
        Driver.add(ConverterThaumcraftItems)
    }
}
