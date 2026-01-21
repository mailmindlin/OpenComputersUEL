package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility

// Note-to-self: this has a component to allow the robot telling it has the
// upgrade.
sealed class UpgradeAngel : ManagedEnvironmentKt(), DeviceInfoKt {
    override val node: Node = Network.newNode(this, Visibility.Network)
        .create()

    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Generic,
        DeviceAttribute.Description to "Angel upgrade",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "FreePlacer (TM)",
        DeviceAttribute.Capacity to Settings.get.maxNetworkPacketSize.toString()
    )
}
