package li.cil.oc.integration.enderstorage

import li.cil.oc.api.Driver
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

object ModEnderStorage : ModProxy() {
    override fun getMod() = Mods.EnderStorage

    override fun initialize() {
        Driver.add(DriverFrequencyOwner())
    }
}
