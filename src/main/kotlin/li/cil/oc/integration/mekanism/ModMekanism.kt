package li.cil.oc.integration.mekanism

import li.cil.oc.integration.Mod

import li.cil.oc.api.IMC
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModMekanism : ModProxy {
    override val mod: Mod = Mods.Mekanism

    override fun initialize() {
        IMC.registerWrenchTool("li.cil.oc.integration.mekanism.EventHandlerMekanism.useWrench")
        IMC.registerWrenchToolCheck("li.cil.oc.integration.mekanism.EventHandlerMekanism.isWrench")
    }
}
