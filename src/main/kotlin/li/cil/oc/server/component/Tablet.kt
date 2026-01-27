package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ComponentConnector
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.item.TabletWrapper

class Tablet(val tablet: TabletWrapper): ManagedEnvironmentKt(), DeviceInfo {
  override val node = nodeFactory(Visibility.Network)
    .withComponent("tablet")
    .withConnector(Settings.get.bufferTablet)
    .create()

  override fun node(): ComponentConnector = node

  private val deviceInfo_ by lazy {
    mapOf(
      DeviceAttribute.Class to DeviceClass.System,
      DeviceAttribute.Description to "Tablet",
      DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
      DeviceAttribute.Product to "Jogger",
      DeviceAttribute.Capacity to tablet.sizeInventory.toString()
    )
  }

  override fun getDeviceInfo() = deviceInfo_

  // ----------------------------------------------------------------------- //

  @Suppress("unused", "unused_parameter")
  @Callback(doc = """function():number -- Gets the pitch of the player holding the tablet.""")
  fun getPitch(context: Context, args: Arguments): Result = result(tablet.player.rotationPitch)

  @Suppress("unused", "unused_parameter")
  @Callback(doc = """function():number -- Gets the yaw of the player holding the tablet.""")
  fun getYaw(context: Context, args: Arguments): Result = result(tablet.player.rotationYaw)
}
