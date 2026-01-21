package li.cil.oc.integration.wrcbe

import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.WirelessRedstone

object ModWRCBE : ModProxy() {
    override fun getMod(): Mods.SimpleMod = Mods.WirelessRedstoneCBE

    override fun initialize() {
        WirelessRedstone.systems.add(WirelessRedstoneCBE)
    }
}
