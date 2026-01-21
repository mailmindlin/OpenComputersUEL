package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.tileentity.traits.BundledRedstoneAware
import li.cil.oc.common.tileentity.traits.RedstoneAware

object Redstone {

    class Vanilla(val redstone: EnvironmentHost with RedstoneAware) : RedstoneVanilla()

    class Bundled(val redstone: EnvironmentHost with BundledRedstoneAware)
        : RedstoneVanilla(), RedstoneBundled

    class Wireless(val redstone: EnvironmentHost) : RedstoneWireless()

    class VanillaWireless(val redstone: EnvironmentHost with RedstoneAware)
        : RedstoneVanilla(), RedstoneWireless

    class BundledWireless(val redstone: EnvironmentHost with BundledRedstoneAware)
        : RedstoneVanilla(), RedstoneBundled, RedstoneWireless {

        private val deviceInfo = mapOf(
            DeviceAttribute.Class to DeviceClass.Communication,
            DeviceAttribute.Description to "Combined redstone controller",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Rx900-M",
            DeviceAttribute.Capacity to "65536",
            DeviceAttribute.Width to "16"
        )

        override fun getDeviceInfo(): MutableMap<String, String> = deviceInfo.toMutableMap()
    }
}
