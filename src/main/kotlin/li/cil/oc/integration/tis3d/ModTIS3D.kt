package li.cil.oc.integration.tis3d

import li.cil.oc.integration.Mod

import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModTIS3D : ModProxy {
    override val mod: Mod = Mods.TIS3D

    override fun initialize() {
        SerialInterfaceProviderAdapter.init()
    }
}
