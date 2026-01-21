package li.cil.oc.integration.minecraftforge

import li.cil.oc.api
import li.cil.oc.integration.Mod
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import net.minecraftforge.common.MinecraftForge

object ModMinecraftForge : ModProxy {
    override fun getMod(): Mod = Mods.Forge

    override fun initialize() {
        MinecraftForge.EVENT_BUS.register(EventHandlerMinecraftForge)
        api.IMC.registerItemCharge(
            "MinecraftForge",
            "li.cil.oc.integration.minecraftforge.EventHandlerMinecraftForge.canCharge",
            "li.cil.oc.integration.minecraftforge.EventHandlerMinecraftForge.charge"
        )
        api.Driver.add(DriverEnergyStorage)
    }
}
