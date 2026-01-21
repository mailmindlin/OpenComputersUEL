package li.cil.oc.integration.ec

import li.cil.oc.api.Driver
import li.cil.oc.integration.Mod
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModExtraCells : ModProxy {
    override val mod: Mod = Mods.ExtraCells

    override fun initialize() {
        Driver.add(DriverController)
        Driver.add(DriverBlockInterface)

        Driver.add(DriverController.Provider)
        Driver.add(DriverBlockInterface.Provider)
    }
}
