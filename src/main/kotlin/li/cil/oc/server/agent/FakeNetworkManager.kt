package li.cil.oc.server.agent

import io.netty.util.concurrent.Future
import io.netty.util.concurrent.GenericFutureListener
import net.minecraft.network.EnumPacketDirection
import net.minecraft.network.NetworkManager
import net.minecraft.network.Packet

object FakeNetworkManager : NetworkManager(EnumPacketDirection.CLIENTBOUND) {
    override fun sendPacket(packetIn: Packet<*>) {}

    override fun sendPacket(packetIn: Packet<*>, listener: GenericFutureListener<out Future<in Void>>, vararg listeners: GenericFutureListener<out Future<in Void>>) {}
}
