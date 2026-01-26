package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.Visibility

class CPU(val tier: Int) : ManagedEnvironmentKt(), DeviceInfo {
    override val node = nodeFactory(Visibility.Neighbors).create()

    private val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Processor,
        DeviceAttribute.Description to "CPU",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "FlexiArch ${tier + 1} Processor",
        DeviceAttribute.Clock to (Settings.get.callBudgets[tier] * 1000).toInt().toString()
    )

    override fun getDeviceInfo() = deviceInfo
}
