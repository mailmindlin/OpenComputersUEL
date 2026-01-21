package li.cil.oc.server.network

import li.cil.oc.api.network.Packet
import java.util.WeakHashMap

internal object DebugNetwork {
  private val cards = WeakHashMap<DebugNode, Unit>()

  fun add(card: DebugNode) {
    cards.put(card, Unit)
  }

  fun remove(card: DebugNode) {
    cards.remove(card)
  }

  fun getEndpoint(tunnel: String): Iterable<DebugNode> = cards.keys.filter { it.address == tunnel }
}

internal interface DebugNode {
  val address: String
  fun receivePacket(packet: Packet)
}