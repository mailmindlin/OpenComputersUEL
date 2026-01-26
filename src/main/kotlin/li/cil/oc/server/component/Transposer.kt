package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.common.tileentity.Transposer as TETransposer
import li.cil.oc.server.PacketSender as ServerPacketSender
import li.cil.oc.server.component.traits.InventoryTransfer
import li.cil.oc.server.component.traits.WorldInventoryAnalytics
import li.cil.oc.server.component.traits.WorldTankAnalytics
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.checkSideAny

object Transposer {
    abstract sealed class Common : ManagedEnvironmentKt(), WorldInventoryAnalytics, WorldTankAnalytics, InventoryTransfer, DeviceInfoKt {
        override val node = newComponentConnector(Visibility.Network, "transposer")

        override val deviceInfo = mapOf(
            DeviceAttribute.Class to DeviceClass.Generic,
            DeviceAttribute.Description to "Transposer",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "TP4k-iX"
        )

        override fun checkSideForAction(args: Arguments, n: Int) = args.checkSideAny(n)

        override fun onTransferContents(): String? {
            return if (node.tryChangeBuffer(-Settings.get.transposerCost)) {
                null
            } else {
                "not enough energy"
            }
        }
    }

    internal class Block(val host: TETransposer) : Common() {
        override val position: BlockPosition
            get() = BlockPosition(host)

        override fun onTransferContents(): String? {
            val result = super.onTransferContents()
            if (result == null) {
                ServerPacketSender.sendTransposerActivity(host)
            }
            return result
        }
    }

    class Upgrade(val host: EnvironmentHost) : Common() {
        init {
            node.setVisibility(Visibility.Neighbors)
        }

        override val position: BlockPosition
            get() = BlockPosition(host)
    }
}
