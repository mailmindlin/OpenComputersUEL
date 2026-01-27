package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Agent
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.server.component.traits.InventoryAnalytics
import li.cil.oc.server.component.traits.InventoryWorldControlMk2
import li.cil.oc.server.component.traits.ItemInventoryControl
import li.cil.oc.server.component.traits.WorldInventoryAnalytics
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.checkSideAny
import li.cil.oc.util.checkSideForAction
import li.cil.oc.common.tileentity.Robot as RobotTileEntity

object UpgradeInventoryController {

    interface Common : DeviceInfo {
        override fun getDeviceInfo() = Companion.deviceInfo
        companion object {
            val deviceInfo = mapOf(
                DeviceAttribute.Class to DeviceClass.Generic,
                DeviceAttribute.Description to "Inventory controller",
                DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
                DeviceAttribute.Product to "Item Cataloguer R1"
            )
        }
    }

    class Adapter(val host: EnvironmentHost) : ManagedEnvironmentKt(), WorldInventoryAnalytics, Common {
        override val node = nodeFactory(Visibility.Network)
            .withComponent("inventory_controller", Visibility.Network)
            .create()

        // ----------------------------------------------------------------------- //

        override val position get() = BlockPosition(host)

        override fun checkSideForAction(args: Arguments, n: Int) = args.checkSideAny(n)
    }

    class Drone(val host: EnvironmentHost) : ManagedEnvironmentKt(), InventoryAnalytics, InventoryWorldControlMk2, WorldInventoryAnalytics, ItemInventoryControl, Common {
        private val agent: Agent
            get() = host as Agent

        override val node: Node = nodeFactory(Visibility.Network)
            .withComponent("inventory_controller", Visibility.Neighbors)
            .create()

        // ----------------------------------------------------------------------- //

        override val position get() = BlockPosition(host)

        override val inventory get() = agent.mainInventory()

        override var selectedSlot: Int
            get() = agent.selectedSlot()
            set(value) = agent.setSelectedSlot(value)

        override fun checkSideForAction(args: Arguments, n: Int) = args.checkSideAny(n)
    }

    class Robot(val host: RobotTileEntity) : ManagedEnvironmentKt(), InventoryAnalytics, InventoryWorldControlMk2, WorldInventoryAnalytics, ItemInventoryControl, Common {
        override val node = nodeFactory(Visibility.Network)
            .withComponent("inventory_controller", Visibility.Neighbors)
            .create()

        // ----------------------------------------------------------------------- //

        override val position get() = BlockPosition(host)

        override val inventory get() = host.mainInventory()

        override var selectedSlot: Int
            get() = host.selectedSlot()
            set(value) {
                host.setSelectedSlot(value)
            }

        override fun checkSideForAction(args: Arguments, n: Int) = host.toGlobal(args.checkSideForAction(n))!!

        @Callback(doc = "function():boolean -- Swaps the equipped tool with the content of the currently selected inventory slot.")
        fun equip(context: Context, args: Arguments): Array<Any?> {
            return if (inventory.sizeInventory > 0) {
                val equipped = host.getStackInSlot(0)
                val selected = inventory.getStackInSlot(selectedSlot)
                host.setInventorySlotContents(0, selected)
                inventory.setInventorySlotContents(selectedSlot, equipped)
                result(true)
            } else {
                result(false)
            }
        }
    }
}
