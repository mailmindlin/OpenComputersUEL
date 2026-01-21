package li.cil.oc.server.network

import li.cil.oc.api.network.Packet
import java.util.WeakHashMap

interface QuantumNode {
  val tunnel: String
  fun receivePacket(packet: Packet)
}

// Just because the name is so fancy!
object QuantumNetwork {
  val tunnels = mutableMapOf<String, WeakHashMap<QuantumNode, Unit>>()

  fun add(card: QuantumNode) {
    tunnels.getOrPut(card.tunnel) { WeakHashMap() }[card] = Unit
  }

  fun remove(card: QuantumNode) {
    tunnels[card.tunnel]?.remove(card)
  }

  fun getEndpoints(tunnel: String): Set<QuantumNode> = tunnels[tunnel]?.keys ?: emptySet()
}
