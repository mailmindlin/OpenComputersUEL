package li.cil.oc.integration.top

import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import net.minecraftforge.fml.common.event.FMLInterModComms

internal object ModTheOneProbe: ModProxy {
    override val mod get() = Mods.TheOneProbe

    override fun initialize() {
        FMLInterModComms.sendFunctionMessage(mod.id, "getTheOneProbe", "li.cil.oc.integration.top.GetTheOneProbe");
    }
}