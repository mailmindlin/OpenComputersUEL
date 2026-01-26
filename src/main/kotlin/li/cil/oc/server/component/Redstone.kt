package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.tileentity.traits.BundledRedstoneAware
import li.cil.oc.common.tileentity.traits.RedstoneAware

object Redstone {

    class Vanilla<T>(override val redstone: T) : RedstoneVanilla<T>()
        where T : EnvironmentHost, T : RedstoneAware

    class Bundled<T>(override val redstone: T) : RedstoneBundled<T>()
        where T : EnvironmentHost, T : BundledRedstoneAware

    class Wireless(override val redstone: EnvironmentHost) : RedstoneWireless()

    class VanillaWireless<T>(val host: T) : RedstoneVanilla<T>(), DeviceInfo
        where T : EnvironmentHost, T : RedstoneAware {

        override val redstone: T get() = host

        private val wirelessComponent = object : RedstoneWireless() {
            override val redstone: EnvironmentHost get() = host
        }

        var wirelessFrequency: Int
            get() = wirelessComponent.wirelessFrequency
            set(value) { wirelessComponent.wirelessFrequency = value }

        var wirelessInput: Boolean
            get() = wirelessComponent.wirelessInput
            set(value) { wirelessComponent.wirelessInput = value }

        var wirelessOutput: Boolean
            get() = wirelessComponent.wirelessOutput
            set(value) { wirelessComponent.wirelessOutput = value }
    }

    class BundledWireless<T>(val host: T) : RedstoneBundled<T>(), DeviceInfo
        where T : EnvironmentHost, T : BundledRedstoneAware {

        override val redstone: T get() = host

        private val wirelessComponent = object : RedstoneWireless() {
            override val redstone: EnvironmentHost get() = host
        }

        var wirelessFrequency: Int
            get() = wirelessComponent.wirelessFrequency
            set(value) { wirelessComponent.wirelessFrequency = value }

        var wirelessInput: Boolean
            get() = wirelessComponent.wirelessInput
            set(value) { wirelessComponent.wirelessInput = value }

        var wirelessOutput: Boolean
            get() = wirelessComponent.wirelessOutput
            set(value) { wirelessComponent.wirelessOutput = value }

        override val deviceInfo = Companion.deviceInfo

        companion object {
            val deviceInfo = mapOf(
                DeviceAttribute.Class to DeviceClass.Communication,
                DeviceAttribute.Description to "Combined redstone controller",
                DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
                DeviceAttribute.Product to "Rx900-M",
                DeviceAttribute.Capacity to "65536",
                DeviceAttribute.Width to "16"
            )
        }
    }
}
