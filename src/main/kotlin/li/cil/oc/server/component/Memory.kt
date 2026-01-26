package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.Visibility

class Memory(val tier: Int): ManagedEnvironmentKt(), DeviceInfoKt {
  override val node = nodeFactory().create()

  override val deviceInfo: Map<String, String> = mapOf(
    DeviceAttribute.Class to DeviceClass.Memory,
    DeviceAttribute.Description to "Memory bank",
    DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
    DeviceAttribute.Product to "Multipurpose RAM Type",
    DeviceAttribute.Clock to (Settings.get.callBudgets[tier] * 1000).toInt().toString()
  )
}
