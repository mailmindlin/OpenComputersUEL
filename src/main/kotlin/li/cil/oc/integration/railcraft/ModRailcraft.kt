package li.cil.oc.integration.railcraft

import li.cil.oc.integration.Mod

import li.cil.oc.api.IMC
import li.cil.oc.api.Driver
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModRailcraft : ModProxy {
    override val mod: Mod = Mods.Railcraft

    override fun initialize() {
        IMC.registerWrenchTool("li.cil.oc.integration.railcraft.EventHandlerRailcraft.useWrench")
        IMC.registerWrenchToolCheck("li.cil.oc.integration.railcraft.EventHandlerRailcraft.isWrench")

        Driver.add(DriverWorldspike)
    }
}
