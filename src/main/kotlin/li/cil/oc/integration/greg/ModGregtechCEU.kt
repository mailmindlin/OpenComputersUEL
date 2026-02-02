package li.cil.oc.integration.greg

import li.cil.oc.integration.Mod

import li.cil.oc.api.Driver
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import net.minecraftforge.common.MinecraftForge

internal object ModGregtechCEU : ModProxy {
    override val mod: Mod get() = Mods.Gregtech

    override fun initialize() {
        Driver.add(DriverGhostCircuitCircuit)
    }
}
