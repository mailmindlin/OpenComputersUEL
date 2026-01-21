package li.cil.oc.integration.tis3d

import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

object ModTIS3D : ModProxy() {
    override fun getMod() = Mods.TIS3D

    override fun initialize() {
        SerialInterfaceProviderAdapter.init()
    }
}
