package li.cil.oc.integration.appeng

import li.cil.oc.integration.Mod

import appeng.api.AEApi
import li.cil.oc.api.Driver
import li.cil.oc.api.IMC
import li.cil.oc.common.tileentity.Print
import li.cil.oc.integration.ModProxy
import li.cil.oc.integration.Mods

internal object ModAppEng : ModProxy {
  override val mod: Mod = Mods.AppliedEnergistics2

  override fun initialize() {
    IMC.registerWrenchTool("li.cil.oc.integration.appeng.EventHandlerAE2.useWrench")
    IMC.registerWrenchToolCheck("li.cil.oc.integration.appeng.EventHandlerAE2.isWrench")

    AEApi.instance().registries().movable().whiteListTileEntity(Print::class.java)

    Driver.add(DriverController)
    Driver.add(DriverExportBus)
    Driver.add(DriverImportBus)
    Driver.add(DriverPartInterface)
    Driver.add(DriverBlockInterface)

    Driver.add(ConverterCellInventory())

    Driver.add(DriverController.Provider)
    Driver.add(DriverExportBus.Provider)
    Driver.add(DriverImportBus.Provider)
    Driver.add(DriverPartInterface.Provider)
    Driver.add(DriverBlockInterface.Provider)
  }
}
