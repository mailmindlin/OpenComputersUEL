package li.cil.oc.common.capabilities

import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.Network
import li.cil.oc.integration.Mods
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilityProvider

object CapabilityEnvironment {
    @JvmField
    val ProviderEnvironment = ResourceLocation(Mods.IDs.OpenComputers, "environment")

    class Provider(val tileEntity: TileEntity) : ICapabilityProvider, Environment {
        private val environmentTileEntity: Environment = tileEntity as Environment

        override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
            return capability == Capabilities.EnvironmentCapability
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
            return if (hasCapability(capability, facing)) this as T else null
        }

        override fun node(): Node? = environmentTileEntity.node()

        override fun onMessage(message: Message) = environmentTileEntity.onMessage(message)

        override fun onConnect(node: Node) = environmentTileEntity.onConnect(node)

        override fun onDisconnect(node: Node) = environmentTileEntity.onDisconnect(node)
    }

    class DefaultImpl : Environment {
        val node: Node? = Network.newNode(this, Visibility.None)!!.create()
        override fun node(): Node? = node

        override fun onMessage(message: Message) {}

        override fun onConnect(node: Node) {}

        override fun onDisconnect(node: Node) {}
    }

    class DefaultStorage : Capability.IStorage<Environment> {
        override fun writeNBT(capability: Capability<Environment>, t: Environment, facing: EnumFacing?): NBTBase? {
            val node = t.node()
            return if (node != null) {
                val nbt = NBTTagCompound()
                node.save(nbt)
                nbt
            } else null
        }

        override fun readNBT(capability: Capability<Environment>, t: Environment, facing: EnumFacing?, nbtBase: NBTBase?) {
            when (nbtBase) {
                is NBTTagCompound -> {
                    val node = t.node()
                    node?.load(nbtBase)
                }
            }
        }
    }
}
