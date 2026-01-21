package li.cil.oc.integration.wrcbe

import li.cil.oc.integration.Mod

import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import li.cil.oc.integration.util.WirelessRedstone

internal object ModWRCBE : ModProxy {
    override val mod: Mod = Mods.WirelessRedstoneCBE

    override fun initialize() {
        WirelessRedstone.systems.add(WirelessRedstoneCBE)
    }
}
