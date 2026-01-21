package li.cil.oc.integration.enderio

import li.cil.oc.integration.Mod

import li.cil.oc.api.IMC
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModEnderIO : ModProxy {
    override val mod: Mod = Mods.EnderIO

    override fun initialize() {
        IMC.registerWrenchTool("li.cil.oc.integration.enderio.EventHandlerEnderIO.useWrench")
        IMC.registerWrenchToolCheck("li.cil.oc.integration.enderio.EventHandlerEnderIO.isWrench")
    }
}
