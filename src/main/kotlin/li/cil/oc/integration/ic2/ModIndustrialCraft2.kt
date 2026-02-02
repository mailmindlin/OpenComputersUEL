package li.cil.oc.integration.ic2

import li.cil.oc.integration.Mod

import li.cil.oc.api.IMC
import li.cil.oc.api.Driver
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods
import li.cil.oc.integration.driverFor
import net.minecraftforge.common.MinecraftForge

internal object ModIndustrialCraft2 : ModProxy {
    override val mod: Mod = Mods.IndustrialCraft2

    fun tryAddDriver(driver: DriverSidedTileEntity) {
        try {
            if (driver.getTileEntityClass() != null) {
                Driver.add(driver)
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun initialize() {
        IMC.registerToolDurabilityProvider("li.cil.oc.integration.ic2.EventHandlerIndustrialCraft2.getDurability")
        IMC.registerWrenchTool("li.cil.oc.integration.ic2.EventHandlerIndustrialCraft2.useWrench")
        IMC.registerWrenchToolCheck("li.cil.oc.integration.ic2.EventHandlerIndustrialCraft2.isWrench")
        IMC.registerItemCharge(
            "IndustrialCraft2",
            "li.cil.oc.integration.ic2.EventHandlerIndustrialCraft2.canCharge",
            "li.cil.oc.integration.ic2.EventHandlerIndustrialCraft2.charge"
        )

        MinecraftForge.EVENT_BUS.register(EventHandlerIndustrialCraft2)

        tryAddDriver(DriverReactorRedstonePort())
        tryAddDriver(DriverMassFab())

        Driver.add(driverFor(::EnergyConductorEnvironment))
        Driver.add(DriverEnergy())
        Driver.add(DriverReactor())
        Driver.add(DriverReactorChamber())

        Driver.add(ConverterElectricItem())
    }
}
