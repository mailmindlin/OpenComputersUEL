package li.cil.oc.common.event

import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.server.component.WirelessNetworkCard
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object WirelessNetworkCardHandler {
    @JvmStatic
    @SubscribeEvent
    fun onMove(e: RobotMoveEvent.Post) {
        val machineNode = e.agent.machine().node()!!
        machineNode.reachableNodes().forEach { node ->
            val host = node.host()
            if (host is WirelessNetworkCard) {
                ApiNetwork.updateWirelessNetwork(host)
            }
        }
    }
}
