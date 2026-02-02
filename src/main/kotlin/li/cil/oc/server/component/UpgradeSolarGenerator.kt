package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.BlockPosition
import net.minecraft.util.EnumFacing

class UpgradeSolarGenerator(val host: EnvironmentHost) : ManagedEnvironmentKt(), DeviceInfo {
    override val node = nodeFactory(Visibility.Network)
        .withConnector()
        .create()

    private var ticksUntilCheck = 0
    private var isSunShining = false

    override fun getDeviceInfo() = Companion.deviceInfo
    companion object {
        val deviceInfo = mapOf(
            DeviceAttribute.Class to DeviceClass.Power,
            DeviceAttribute.Description to "Solar panel",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Enligh10"
        )
    }

    // ----------------------------------------------------------------------- //

    override fun canUpdate(): Boolean = true

    override fun update() {
        super.update()

        ticksUntilCheck -= 1
        if (ticksUntilCheck <= 0) {
            ticksUntilCheck = 100
            isSunShining = isSunVisible
        }
        if (isSunShining) {
            node!!.changeBuffer(Settings.get.solarGeneratorEfficiency)
        }
    }

    private val isSunVisible: Boolean
        get() {
            val blockPos = BlockPosition(host).offset(EnumFacing.UP)
            return host.world.isDaytime &&
                    !host.world.provider.isNether &&
                    host.world.canBlockSeeSky(blockPos.toBlockPos()) &&
                    (!host.world.getBiome(blockPos.toBlockPos()).canRain() || (!host.world.isRaining && !host.world.isThundering))
        }
}
