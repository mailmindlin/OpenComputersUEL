package li.cil.oc.common.event

import li.cil.oc.api.event.NetworkActivityEvent
import li.cil.oc.api.internal.Rack
import li.cil.oc.common.tileentity.Case
import li.cil.oc.server.component.Server
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object NetworkActivityHandler {
    @JvmStatic
    @SubscribeEvent
    fun onNetworkActivity(e: NetworkActivityEvent.Server) {
        val tileEntity = e.tileEntity
        if (tileEntity is Rack) {
            for (slot in 0 until tileEntity.sizeInventory) {
                val mountable = tileEntity.getMountable(slot)
                if (mountable is Server) {
                    val containsNode = mountable.componentSlot(e.node.address()!!) >= 0
                    if (containsNode) {
                        mountable.lastNetworkActivity = System.currentTimeMillis()
                        tileEntity.markChanged(slot)
                    }
                }
            }
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onNetworkActivity(e: NetworkActivityEvent.Client) {
        val tileEntity = e.tileEntity
        if (tileEntity is Case) {
            tileEntity.lastNetworkActivity = System.currentTimeMillis()
        }
    }
}
