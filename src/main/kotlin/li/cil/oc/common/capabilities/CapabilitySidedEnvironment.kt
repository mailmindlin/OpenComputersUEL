package li.cil.oc.common.capabilities

import li.cil.oc.api.network.Node
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.integration.Mods
import net.minecraft.nbt.NBTBase
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider

object CapabilitySidedEnvironment {
    @JvmField
    val ProviderSidedEnvironment = ResourceLocation(Mods.IDs.OpenComputers, "sided_environment")

    class Provider(val tileEntity: TileEntity) : ICapabilityProvider, SidedEnvironment {
        private val sidedEnvironmentTileEntity: SidedEnvironment = tileEntity as SidedEnvironment

        override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
            return capability == Capabilities.SidedEnvironmentCapability
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
            return if (hasCapability(capability, facing)) this as T else null
        }

        override fun sidedNode(side: EnumFacing): Node? = sidedEnvironmentTileEntity.sidedNode(side)

        override fun canConnect(side: EnumFacing): Boolean = sidedEnvironmentTileEntity.canConnect(side)
    }

    class DefaultImpl : SidedEnvironment {
        override fun sidedNode(side: EnumFacing): Node? = null

        override fun canConnect(side: EnumFacing): Boolean = false
    }

    class DefaultStorage : Capability.IStorage<SidedEnvironment> {
        override fun writeNBT(capability: Capability<SidedEnvironment>, t: SidedEnvironment, enumFacing: EnumFacing?): NBTBase? = null

        override fun readNBT(capability: Capability<SidedEnvironment>, t: SidedEnvironment, enumFacing: EnumFacing?, nbtBase: NBTBase?) {}
    }
}
