package li.cil.oc.integration.enderio

import li.cil.oc.api
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

object ModEnderIO : ModProxy() {
    override fun getMod() = Mods.EnderIO

    override fun initialize() {
        api.IMC.registerWrenchTool("li.cil.oc.integration.enderio.EventHandlerEnderIO.useWrench")
        api.IMC.registerWrenchToolCheck("li.cil.oc.integration.enderio.EventHandlerEnderIO.isWrench")
    }
}
