package li.cil.oc.server

import li.cil.oc.OpenComputers
import li.cil.oc.common.Proxy as CommonProxy
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.network.NetworkRegistry

private class Proxy: CommonProxy() {
  override fun init(e: FMLInitializationEvent) {
    super.init(e)

    NetworkRegistry.INSTANCE.registerGuiHandler(OpenComputers.INSTANCE, GuiHandler)
  }
}
