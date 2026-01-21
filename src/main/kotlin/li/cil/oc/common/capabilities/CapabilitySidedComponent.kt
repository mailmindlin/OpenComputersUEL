package li.cil.oc.common.capabilities

import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.SidedComponent
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.integration.Mods
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider

object CapabilitySidedComponent {
    @JvmField
    val SidedComponent = ResourceLocation(Mods.IDs.OpenComputers, "sided_component")

    class Provider(val tileEntity: TileEntity) : ICapabilityProvider, SidedEnvironment {
        private val environmentTileEntity: Environment = tileEntity as Environment
        private val sidedComponentTileEntity: li.cil.oc.api.network.SidedComponent = tileEntity as li.cil.oc.api.network.SidedComponent

        override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
            return capability == Capabilities.SidedEnvironmentCapability
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
            return if (hasCapability(capability, facing)) this as T else null
        }

        override fun sidedNode(side: EnumFacing): Node? =
            if (sidedComponentTileEntity.canConnectNode(side)) environmentTileEntity.node() else null

        override fun canConnect(side: EnumFacing): Boolean = sidedComponentTileEntity.canConnectNode(side)
    }
}
