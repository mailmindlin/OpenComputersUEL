package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import li.cil.oc.common.tileentity.traits.PowerAcceptor as TraitPowerAcceptor
import li.cil.oc.common.tileentity.traits.Environment as TraitEnvironment
import li.cil.oc.common.tileentity.traits.NotAnalyzable as TraitNotAnalyzable

class PowerConverter : TileEntityBase(), TraitPowerAcceptor(), TraitEnvironment, TraitNotAnalyzable, DeviceInfo {
    @JvmField
    val node: Connector = ApiNetwork.newNode(this, Visibility.None)
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

    override fun getDeviceInfo(): Map<String, String> = deviceInfo as Map<String, String>

    @SideOnly(Side.CLIENT)
    override fun hasConnector(side: EnumFacing): Boolean = true

    override fun connector(side: EnumFacing): Connector = node

    override fun energyThroughput(): Double = Settings.get.powerConverterRate
}
