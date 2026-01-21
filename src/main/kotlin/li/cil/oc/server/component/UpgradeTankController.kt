package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.server.component.traits.TankInventoryControl
import li.cil.oc.server.component.traits.WorldTankAnalytics
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.checkSideAny
import li.cil.oc.util.checkSideForAction
import li.cil.oc.common.tileentity.Robot as TERobot

object UpgradeTankController {

    interface Common : DeviceInfo {
        override fun getDeviceInfo(): MutableMap<String, String> = mapOf(
            DeviceAttribute.Class to DeviceClass.Generic,
            DeviceAttribute.Description to "Tank controller",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "FlowCheckDX"
        ).toMutableMap()
    }

    sealed class Adapter(val host: EnvironmentHost) : ManagedEnvironmentKt(), WorldTankAnalytics, Common {
        override val node = Network.newNode(this, Visibility.Network)
            .withComponent("tank_controller", Visibility.Network)
            .create()

        // ----------------------------------------------------------------------- //

        override val position get() = BlockPosition(host)

        override fun checkSideForAction(args: Arguments, n: Int) = args.checkSideAny(n)
    }

    sealed class Drone(val host: EnvironmentHost) : ManagedEnvironmentKt(), TankInventoryControl, WorldTankAnalytics, Common {
        private val agent: Agent
            get() = host as Agent

        override val node = Network.newNode(this, Visibility.Network)
            .withComponent("tank_controller", Visibility.Neighbors)
            .create()

        override val position get() = BlockPosition(host)

        override val inventory get() = agent.mainInventory()

        override var selectedSlot: Int
            get() = agent.selectedSlot()
            set(value) = agent.setSelectedSlot(value)

        override val tank get() = agent.tank()

        override var selectedTank: Int
            get() = agent.selectedTank()
            set(value) = agent.setSelectedTank(value)

        override fun checkSideForAction(args: Arguments, n: Int) = args.checkSideAny(n)
    }

    sealed class Robot(val host: TERobot) : ManagedEnvironmentKt(), TankInventoryControl, WorldTankAnalytics, Common {
        override val node = Network.newNode(this, Visibility.Network)
            .withComponent("tank_controller", Visibility.Neighbors)
            .create()

        override val position get(): BlockPosition = BlockPosition(host)

        override val inventory get() = host.mainInventory

        override var selectedSlot: Int
            get() = host.selectedSlot
            set(value) {
                host.selectedSlot = value
            }

        override val tank get() = host.tank

        override var selectedTank: Int
            get() = host.selectedTank
            set(value) {
                host.selectedTank = value
            }

        override fun checkSideForAction(args: Arguments, n: Int) = host.toGlobal(args.checkSideForAction(n))
    }
}
