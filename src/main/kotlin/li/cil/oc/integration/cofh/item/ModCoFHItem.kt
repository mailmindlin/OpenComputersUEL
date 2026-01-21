package li.cil.oc.integration.cofh.item

import li.cil.oc.integration.Mod

import li.cil.oc.api.IMC
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModCoFHItem : ModProxy {
    override val mod: Mod = Mods.CoFHCore

    override fun initialize() {
        IMC.registerWrenchTool("li.cil.oc.integration.cofh.item.EventHandlerCoFH.useWrench")
        IMC.registerWrenchToolCheck("li.cil.oc.integration.cofh.item.EventHandlerCoFH.isWrench")
    }
}
