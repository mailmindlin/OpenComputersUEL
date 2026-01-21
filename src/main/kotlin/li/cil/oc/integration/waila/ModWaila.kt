package li.cil.oc.integration.waila

import li.cil.oc.integration.Mod

import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import net.minecraftforge.fml.common.event.FMLInterModComms

internal object ModWaila : ModProxy {
    override val mod: Mod = Mods.Waila

    override fun initialize() {
        FMLInterModComms.sendMessage(Mods.IDs.Waila, "register", "li.cil.oc.integration.waila.BlockDataProvider.init")
    }
}
