package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

class PowerConverter : TileEntityBase(), traits.PowerAcceptor(), traits.Environment, traits.NotAnalyzable, DeviceInfo {
    @JvmField
    val node: Connector = api.Network.newNode(this, Visibility.None)
        .withConnector(Settings.get.bufferConverter)
        .create()

    override fun getNode(): Node = node

    private val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Power,
            DeviceAttribute.Description to "Power converter",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Transgizer-PX5",
            DeviceAttribute.Capacity to energyThroughput().toString()
        )
    }

    override fun getDeviceInfo(): java.util.Map<String, String> = deviceInfo as java.util.Map<String, String>

    @SideOnly(Side.CLIENT)
    override fun hasConnector(side: EnumFacing): Boolean = true

    override fun connector(side: EnumFacing): Connector = node

    override fun energyThroughput(): Double = Settings.get.powerConverterRate
}
