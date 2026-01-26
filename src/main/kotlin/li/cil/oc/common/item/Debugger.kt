package li.cil.oc.common.item

import li.cil.oc.OpenComputers
import li.cil.oc.api.Network
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.ComponentConnector
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.getTileEntity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.FakePlayer

class Debugger(parent: Delegator) : AbstractDelegate(parent) {
    override fun onItemUse(stack: ItemStack, player: EntityPlayer, position: BlockPosition, side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float): Boolean {
        val world = position.world ?: return false
        if (player is FakePlayer) return false
        if (player !is EntityPlayerMP) return false

        if (!world.isRemote) {
            when (val tileEntity = world.getTileEntity(position)) {
                is SidedEnvironment -> reconnect(tileEntity.sidedNode(side))
                is Environment -> reconnect(tileEntity.node())
                else -> {
                    DebuggerObject.node?.remove()
                }
            }
        }
        return true
    }
    companion object DebuggerObject : Environment {
        @JvmField
        var node: Node? = Network.newNode(this, Visibility.Network)!!.create()

        override fun node(): Node? = node

        override fun onConnect(node: Node) {
            OpenComputers.log.info("[NETWORK DEBUGGER] New node in network: ${nodeInfo(node)}")
        }

        override fun onDisconnect(node: Node) {
            OpenComputers.log.info("[NETWORK DEBUGGER] Node removed from network: ${nodeInfo(node)}")
        }

        override fun onMessage(message: Message) {
            OpenComputers.log.info("[NETWORK DEBUGGER] Received message: ${messageInfo(message)}.")
        }

        @JvmStatic
        private fun reconnect(node: Node?) {
            this.node?.remove()
            Network.joinNewNetwork(this.node)
            if (node != null)
                this.node?.connect(node)
        }

        private fun nodeInfo(node: Node): String {
            val base = "{address = ${node.address()}, reachability = ${node.reachability().name}"
            val extra = when (node) {
                is ComponentConnector -> componentInfo(node) + connectorInfo(node)
                is Component -> componentInfo(node)
                is Connector -> connectorInfo(node)
                else -> ""
            }
            return "$base$extra}"
        }

        private fun componentInfo(component: Component): String =
            ", type = component, name = ${component.name()}, visibility = ${component.visibility().name}"

        private fun connectorInfo(connector: Connector): String =
            ", type = connector, buffer = ${connector.localBuffer()}, bufferSize = ${connector.localBufferSize()}"

        private fun messageInfo(message: Message): String =
            "{name = ${message.name()}, source = ${nodeInfo(message.source())}, data = [${message.data().joinToString(", ")}]}"
    }

}