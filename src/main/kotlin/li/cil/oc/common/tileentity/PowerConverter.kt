package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity.traits.power.AppliedEnergistics2
import li.cil.oc.common.tileentity.traits.power.IndustrialCraft2Experimental
import li.cil.oc.server.component.DeviceInfoKt
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.common.tileentity.traits.NotAnalyzable as TraitNotAnalyzable
import li.cil.oc.common.tileentity.traits.PowerAcceptor as TraitPowerAcceptor

class PowerConverter : TileEntityBase.TEEnvironmentBase(), TraitPowerAcceptor, TraitNotAnalyzable, DeviceInfoKt {
    @JvmField
    val node: Connector = ApiNetwork.newNode(this, Visibility.None)!!
        .withConnector(Settings.get.bufferConverter)
        .create()
    override fun node(): Node = node

    override val ic2Delegate: IndustrialCraft2Experimental.Delegate = register(IndustrialCraft2Experimental::Delegate)
    override val ae2Delegate: AppliedEnergistics2.Delegate = register(AppliedEnergistics2::Delegate)

    override val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Power,
            DeviceAttribute.Description to "Power converter",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "Transgizer-PX5",
            DeviceAttribute.Capacity to energyThroughput.toString()
        )
    }

    @SideOnly(Side.CLIENT)
    override fun hasConnector(side: EnumFacing?): Boolean = true
    override fun connector(side: EnumFacing?): Connector = node

    override val energyThroughput: Double
        get() = Settings.get.powerConverterRate
}
