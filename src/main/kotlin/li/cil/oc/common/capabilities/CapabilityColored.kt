package li.cil.oc.common.capabilities

import li.cil.oc.api.internal.Colored
import li.cil.oc.integration.Mods
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagInt
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider

object CapabilityColored {
    @JvmField
    val ProviderColored = ResourceLocation(Mods.IDs.OpenComputers, "colored")

    class Provider(val tileEntity: TileEntity) : ICapabilityProvider, Colored {
        private val coloredTileEntity: Colored = tileEntity as Colored

        override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
            return capability == Capabilities.ColoredCapability
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
            return if (hasCapability(capability, facing)) this as T else null
        }

        override fun getColor(): Int = coloredTileEntity.color

        override fun setColor(value: Int) = coloredTileEntity.setColor(value)

        override fun controlsConnectivity(): Boolean = coloredTileEntity.controlsConnectivity()
    }

    class DefaultImpl : Colored {
        private var color: Int = 0

        override fun getColor(): Int = color

        override fun setColor(value: Int) {
            color = value
        }

        override fun controlsConnectivity(): Boolean = false
    }

    class DefaultStorage : Capability.IStorage<Colored> {
        override fun writeNBT(capability: Capability<Colored>, t: Colored, enumFacing: EnumFacing?): NBTBase {
            val color = t.color
            return NBTTagInt(color)
        }

        override fun readNBT(capability: Capability<Colored>, t: Colored, enumFacing: EnumFacing?, nbtBase: NBTBase?) {
            when (nbtBase) {
                is NBTTagInt -> t.setColor(nbtBase.int)
            }
        }
    }
}
