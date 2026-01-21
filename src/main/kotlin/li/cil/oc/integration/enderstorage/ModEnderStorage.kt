package li.cil.oc.integration.enderstorage

import li.cil.oc.api.Driver
import li.cil.oc.integration.Mod
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModEnderStorage : ModProxy {
    override val mod: Mod = Mods.EnderStorage

    override fun initialize() {
        Driver.add(DriverFrequencyOwner())
    }
}
