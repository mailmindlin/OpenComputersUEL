package li.cil.oc.integration.minecraftforge

import li.cil.oc.api.IMC
import li.cil.oc.api.Driver
import li.cil.oc.integration.Mod
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import net.minecraftforge.common.MinecraftForge

internal object ModMinecraftForge : ModProxy {
    override val mod: Mod = Mods.Forge

    override fun initialize() {
        MinecraftForge.EVENT_BUS.register(EventHandlerMinecraftForge)
        IMC.registerItemCharge(
            "MinecraftForge",
            "li.cil.oc.integration.minecraftforge.EventHandlerMinecraftForge.canCharge",
            "li.cil.oc.integration.minecraftforge.EventHandlerMinecraftForge.charge"
        )
        Driver.add(DriverEnergyStorage)
    }
}
