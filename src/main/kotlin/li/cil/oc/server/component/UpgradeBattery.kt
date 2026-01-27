package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.Visibility

class UpgradeBattery(val tier: Int) : ManagedEnvironmentKt(), DeviceInfo {
    override val node = nodeFactory(Visibility.Network)
        .withConnector(Settings.get.bufferCapacitorUpgrades[tier])
        .create()

    private val deviceInfo_ by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Power,
            DeviceAttribute.Description to "Battery",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Unlimited Power (Almost Ed.)",
            DeviceAttribute.Capacity to Settings.get.bufferCapacitorUpgrades[tier].toString()
        )
    }

    override fun getDeviceInfo() = deviceInfo_
}
