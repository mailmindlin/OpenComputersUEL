package li.cil.oc.common.event

import li.cil.oc.api.event.RobotPlaceInAirEvent
import li.cil.oc.api.network.Node
import li.cil.oc.server.component.UpgradeAngel
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object AngelUpgradeHandler {
    @JvmStatic
    @SubscribeEvent
    fun onPlaceInAir(e: RobotPlaceInAirEvent) {
        val machineNode = e.agent.machine().node()
        e.setAllowed(machineNode.reachableNodes().any { node ->
            node is Node && node.canBeReachedFrom(machineNode) && node.host() is UpgradeAngel
        })
    }
}
