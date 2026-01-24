package li.cil.oc.server.network

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.detail.Builder
import li.cil.oc.api.network.Message as IMessage
import li.cil.oc.api.network.Network as INetwork
import li.cil.oc.api.network.Packet as IPacket
import li.cil.oc.api.detail.NetworkAPI
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.network.WirelessEndpoint
import li.cil.oc.api.network.Node as ImmutableNode
import li.cil.oc.api.network.Connector as ApiConnector
import li.cil.oc.common.capabilities.Capabilities
import li.cil.oc.common.tileentity.traits.ImmibisMicroblock
import li.cil.oc.util.Color
import li.cil.oc.util.SideTracker
import net.minecraft.item.EnumDyeColor
import net.minecraft.nbt.*
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

// Looking at this again after some time, the similarity to const in C++ is somewhat uncanny.
internal class Network private constructor(private val data: MutableMap<String, Vertex> = mutableMapOf()) : Distributor {
  constructor(node: Node) : this() {
    addNew(node)
    node.onConnect(node)
  }

  override var globalBuffer = 0.0
  override var globalBufferSize = 0.0

  private val connectors = mutableListOf<Connector>()

  private val wrapper: Wrapper by lazy { Wrapper(this) }

  init {
    for (vertex in data.values) {
      val nodeData = vertex.data
      if (nodeData is Connector) {
        addConnector(nodeData)
      }
      nodeData.network = wrapper
    }
  }

  // Called by nodes when they want to change address from loading.
  fun remap(remappedNode: Node, newAddress: String) {
    val vertex = data[remappedNode.address]
    if (vertex != null) {
      val neighbors = vertex.edges.map { it.other(vertex) }
      vertex.data.remove()
      vertex.data.address = newAddress
      while (data.contains(vertex.data.address)) {
        vertex.data.address = UUID.randomUUID().toString()
      }
      if (neighbors.isEmpty()) {
        addNew(vertex.data)
      } else {
        for (neighbor in neighbors) {
          neighbor.data.connect(vertex.data)
        }
      }
    } else {
      throw AssertionError("Node believes it belongs to a network it doesn't.")
    }
  }

  // ----------------------------------------------------------------------- //

  fun connect(nodeA: Node, nodeB: Node): Boolean {
    if (nodeA == nodeB) throw IllegalArgumentException("Cannot connect a node to itself.")

    val containsA = contains(nodeA)
    val containsB = contains(nodeB)

    if (!containsA && !containsB) throw IllegalArgumentException(
      "At least one of the nodes must already be in this network.")

    val oldNodeA by lazy { vertex(nodeA) }
    val oldNodeB by lazy { vertex(nodeB) }

    return if (containsA && containsB) {
      // Both nodes already exist in the network but there is a new connection.
      // This can happen if a new node sequentially connects to multiple nodes
      // in an existing network, e.g. in a setup like so:
      // O O   Where O is an old node, and N is the new Node. It would connect
      // O N   to the node above and left to it (in no particular order).
      if (!oldNodeA.edges.any { it.isBetween(oldNodeA, oldNodeB) }) {
        assert(!oldNodeB.edges.any { it.isBetween(oldNodeA, oldNodeB) })
        Edge(oldNodeA, oldNodeB)
        if (oldNodeA.data.reachability() == Visibility.Neighbors)
          oldNodeB.data.onConnect(oldNodeA.data)
        if (oldNodeB.data.reachability() == Visibility.Neighbors)
          oldNodeA.data.onConnect(oldNodeB.data)
        true
      } else {
        false // That connection already exists.
      }
    } else if (containsA) {
      add(oldNodeA, nodeB)
    } else {
      add(oldNodeB, nodeA)
    }
  }

  fun disconnect(nodeA: Node, nodeB: Node): Boolean {
    if (nodeA == nodeB) throw IllegalArgumentException("Cannot disconnect a node from itself.")

    val containsA = contains(nodeA)
    val containsB = contains(nodeB)

    if (!containsA || !containsB) throw IllegalArgumentException(
      "Both of the nodes must be in this network.")

    val oldNodeA = vertex(nodeA)
    val oldNodeB = vertex(nodeB)

    val edge = oldNodeA.edges.find { it.isBetween(oldNodeA, oldNodeB) }
    return if (edge != null) {
      handleSplit(edge.remove())
      if (edge.left.data.reachability() == Visibility.Neighbors)
        edge.right.data.onDisconnect(edge.left.data)
      if (edge.right.data.reachability() == Visibility.Neighbors)
        edge.left.data.onDisconnect(edge.right.data)
      true
    } else {
      false // That connection doesn't exist.
    }
  }

  fun remove(node: Node): Boolean {
    val entry = data.remove(node.address)
    return if (entry != null) {
      if (node is Connector) {
        removeConnector(node)
      }
      node.network = null
      val subGraphs = entry.remove()
      val targets: Iterable<ImmutableNode> = listOf(node) + when (entry.data.reachability()) {
        Visibility.None -> emptyList()
        Visibility.Neighbors -> entry.edges.map { it.other(entry).data }
        Visibility.Network -> subGraphs.flatMap { it.values.map { v -> v.data } }
      }
      handleSplit(subGraphs)
      for (target in targets) {
        (target as Node).onDisconnect(node)
      }
      true
    } else {
      false
    }
  }

  // ----------------------------------------------------------------------- //

  fun node(address: String): ImmutableNode? = data[address]?.data

  val nodes: Iterable<ImmutableNode> get() = data.values.map { it.data }

  fun reachableNodes(reference: ImmutableNode): Iterable<ImmutableNode> {
    val referenceNeighbors = neighbors(reference).toSet()
    return nodes.filter { node ->
      node != reference && (node.reachability() == Visibility.Network ||
        (node.reachability() == Visibility.Neighbors && referenceNeighbors.contains(node)))
    }
  }

  fun reachingNodes(reference: ImmutableNode): Iterable<ImmutableNode> {
    return when (reference.reachability()) {
      Visibility.Network -> nodes.filter { it != reference }
      Visibility.Neighbors -> {
        val referenceNeighbors = neighbors(reference).toSet()
        nodes.filter { it != reference && referenceNeighbors.contains(it) }
      }
      else -> emptyList()
    }
  }

  fun neighbors(node: ImmutableNode): Iterable<ImmutableNode> {
    val n = data[node.address()]
    if (n != null && n.data == node) {
      return n.edges.map { it.other(n).data }
    }
    throw IllegalArgumentException("Node must be in this network.")
  }

  // ----------------------------------------------------------------------- //

  fun sendToAddress(source: ImmutableNode, target: String, name: String, vararg args: Any?) {
    if (source.network() != wrapper)
      throw IllegalArgumentException("Source node must be in this network.")
    val node = data[target]
    if (node != null && node.data.canBeReachedFrom(source)) {
      send(source, listOf(node.data), name, *args)
    }
  }

  fun sendToNeighbors(source: ImmutableNode, name: String, vararg args: Any?) {
    if (source.network() != wrapper)
      throw IllegalArgumentException("Source node must be in this network.")
    send(source, neighbors(source).filter { it.reachability() != Visibility.None }, name, *args)
  }

  fun sendToReachable(source: ImmutableNode, name: String, vararg args: Any?) {
    if (source.network() != wrapper)
      throw IllegalArgumentException("Source node must be in this network.")
    send(source, reachableNodes(source), name, *args)
  }

  fun sendToVisible(source: ImmutableNode, name: String, vararg args: Any?) {
    if (source.network() != wrapper)
      throw IllegalArgumentException("Source node must be in this network.")
    send(source, reachableNodes(source)
      .filterIsInstance<li.cil.oc.api.network.Component>()
      .filter { it.canBeSeenFrom(source) }, name, *args)
  }

  // ----------------------------------------------------------------------- //

  private fun contains(node: Node) = node.network == wrapper && data.contains(node.address)

  private fun vertex(node: ImmutableNode) = data[node.address()]!!

  private fun addNew(node: Node): Vertex {
    val newVertex = Vertex(node)
    if (node.address == null || data.contains(node.address)) {
      node.address = UUID.randomUUID().toString()
    }
    data[node.address!!] = newVertex
    if (node is Connector) {
      addConnector(node)
    }
    node.network = wrapper
    return newVertex
  }

  private fun add(oldNode: Vertex, addedNode: Node): Boolean {
    // Queue onConnect calls to avoid side effects from callbacks.
    val connects = mutableListOf<Pair<ImmutableNode, Iterable<ImmutableNode>>>()
    // Check if the other node is new or if we have to merge networks.
    if (addedNode.network == null) {
      val newVertex = addNew(addedNode)
      Edge(oldNode, newVertex)
      when (addedNode.reachability()) {
        Visibility.None -> connects.add(addedNode to listOf(addedNode))
        Visibility.Neighbors -> {
          connects.add(addedNode to listOf(addedNode) + neighbors(addedNode))
          for (node in reachingNodes(addedNode)) {
            connects.add(node to listOf(addedNode))
          }
        }
        Visibility.Network -> {
          // Explicitly send to the added node itself first.
          connects.add(addedNode to listOf(addedNode) + nodes.filter { it != addedNode })
          for (node in reachingNodes(addedNode)) {
            connects.add(node to listOf(addedNode))
          }
        }
      }

      // added node may load more internal nodes
      addedNode.onConnect(addedNode)
      val visibleNodes = nodes.filter { it.reachability() == Visibility.Network }
      for (node in visibleNodes) {
        connects.add(node to nodes)
      }
    } else {
      val otherNetwork = (addedNode.network as Wrapper).network

      // If the other network contains nodes with addresses used in our local
      // network we'll have to re-assign those... since dynamically handling
      // changes to one's address is not expected of nodes / hosts, we have to
      // remove and reconnect the nodes. This is a pretty shitty solution, and
      // may break things slightly here and there (e.g. if this is the node of
      // a running machine the computer will most likely crash), but it should
      // never happen in normal operation anyway. It *can* happen when NBT
      // editing stuff or using mods to clone blocks (e.g. WorldEdit).
      val duplicates = otherNetwork.data.filter { data.contains(it.key) }.values.toTypedArray()
      val otherNetworkAfterReaddress = if (duplicates.isEmpty()) {
        otherNetwork
      } else {
        for (vertx in duplicates) {
          val nodeData = vertx.data
          val neighborNodes = vertx.edges.map { it.other(vertx).data }.toTypedArray()

          var newAddress: String
          do {
            newAddress = UUID.randomUUID().toString()
          } while (data.contains(newAddress) || otherNetwork.data.contains(newAddress))

          // This may lead to splits, which is the whole reason we have to
          // check the network of the other nodes after the readdressing.
          nodeData.remove()
          nodeData.address = newAddress
          NetworkObject.joinNewNetwork(nodeData)

          if (nodeData.address == newAddress) {
            for (neighbor in neighborNodes.filter { it.network != null }) {
              neighbor.connect(nodeData)
            }
          } else {
            OpenComputers.log.error("I can't see this happening any other way than someone directly setting node addresses, which they shouldn't. So yeah. Shit'll be borked. Deal with it.")
            nodeData.remove() // well screw you then
          }
        }

        (duplicates[0].data.network as Wrapper).network
      }

      // The address change can theoretically cause the node to be kicked from
      // its old network (via onConnect callbacks), so we make sure it's still
      // in the same network. If it isn't we start over.
      if (addedNode.network != null && (addedNode.network as Wrapper).network == otherNetworkAfterReaddress) {
        if (addedNode.reachability() == Visibility.Neighbors)
          connects.add(addedNode to listOf(oldNode.data))
        if (oldNode.data.reachability() == Visibility.Neighbors)
          connects.add(oldNode.data to listOf(addedNode))

        val oldNodes = nodes
        val newNodes = otherNetworkAfterReaddress.nodes
        val oldVisibleNodes = oldNodes.filter { it.reachability() == Visibility.Network }
        val newVisibleNodes = newNodes.filter { it.reachability() == Visibility.Network }

        for (node in newVisibleNodes) {
          connects.add(node to oldNodes)
        }
        for (node in oldVisibleNodes) {
          connects.add(node to newNodes)
        }

        data.putAll(otherNetworkAfterReaddress.data)
        connectors.addAll(otherNetworkAfterReaddress.connectors)
        globalBuffer += otherNetworkAfterReaddress.globalBuffer
        globalBufferSize += otherNetworkAfterReaddress.globalBufferSize
        for (vertx in otherNetworkAfterReaddress.data.values) {
          if (vertx.data is Connector) {
            (vertx.data as Connector).distributor = wrapper
          }
          vertx.data.network = wrapper
        }
        otherNetworkAfterReaddress.data.clear()
        otherNetworkAfterReaddress.connectors.clear()

        Edge(oldNode, vertex(addedNode))
      } else {
        return add(oldNode, addedNode)
      }
    }

    for ((node, nodeList) in connects) {
      for (n in nodeList) {
        (n as Node).onConnect(node)
      }
    }

    return true
  }

  private fun handleSplit(subGraphs: List<MutableMap<String, Vertex>>) {
    if (subGraphs.size > 1) {
      val nodesList = subGraphs.map { it.values.map { v -> v.data } }
      val visibleNodesList = nodesList.map { it.filter { n -> n.reachability() == Visibility.Network } }

      data.clear()
      connectors.clear()
      globalBuffer = 0.0
      globalBufferSize = 0.0
      data.putAll(subGraphs[0])
      for (vertex in data.values) {
        if (vertex.data is Connector) {
          addConnector(vertex.data as Connector)
        }
      }
      for (i in 1 until subGraphs.size) {
        Network(subGraphs[i])
      }

      for (indexA in subGraphs.indices) {
        val nodesA = nodesList[indexA]
        val visibleNodesA = visibleNodesList[indexA]
        for (indexB in (indexA + 1) until subGraphs.size) {
          val nodesB = nodesList[indexB]
          val visibleNodesB = visibleNodesList[indexB]
          for (node in visibleNodesA) {
            for (n in nodesB) {
              n.onDisconnect(node)
            }
          }
          for (node in visibleNodesB) {
            for (n in nodesA) {
              n.onDisconnect(node)
            }
          }
        }
      }
    }
  }

  private fun send(source: ImmutableNode, targets: Iterable<ImmutableNode>, name: String, vararg args: Any?) {
    val message = Message(source, name, arrayOf(*args))
    for (target in targets) {
      target.host().onMessage(message)
    }
  }

  // ----------------------------------------------------------------------- //

  override fun addConnector(connector: ApiConnector) {
    connector as Connector
    if (connector.localBufferSize > 0) {
      assert(!connectors.contains(connector))
      connectors.add(connector)
      globalBuffer += connector.localBuffer
      globalBufferSize += connector.localBufferSize
    }
    connector.distributor = wrapper
  }

  override fun removeConnector(connector: ApiConnector) {
    connector as Connector
    if (connector.localBufferSize > 0) {
      assert(connectors.contains(connector))
      connectors.remove(connector)
      globalBuffer -= connector.localBuffer
      globalBufferSize -= connector.localBufferSize
    }
  }

  override fun changeBuffer(delta: Double): Double {
    if (delta == 0.0) return 0.0
    if (Settings.get.ignorePower) {
      return if (delta < 0) 0.0 else delta
    }
    return synchronized(this) {
      val oldBuffer = globalBuffer
      globalBuffer = min(max(globalBuffer + delta, 0.0), globalBufferSize)
      if (globalBuffer == oldBuffer) {
        return delta
      }
      if (delta < 0) {
        var remaining = -delta
        for (connector in connectors) {
          if (remaining <= 0) break
          if (connector.localBuffer > 0) {
            if (connector.localBuffer < remaining) {
              remaining -= connector.localBuffer
              connector.localBuffer = 0.0
            } else {
              connector.localBuffer -= remaining
              remaining = 0.0
            }
          }
        }
        -remaining
      } else {
        var remaining = delta
        for (connector in connectors) {
          if (remaining <= 0) break
          if (connector.localBuffer < connector.localBufferSize) {
            val space = connector.localBufferSize - connector.localBuffer
            if (space < remaining) {
              remaining -= space
              connector.localBuffer = connector.localBufferSize
            } else {
              connector.localBuffer += remaining
              remaining = 0.0
            }
          }
        }
        remaining
      }
    }
  }

  // ----------------------------------------------------------------------- //

  private class Vertex(val data: Node) {
    val edges = mutableListOf<Edge>()

    fun remove(): List<MutableMap<String, Vertex>> {
      for (edge in edges) {
        edge.other(this).edges.remove(edge)
      }
      return searchGraphs(edges.map { it.other(this) })
    }

    override fun toString() = "$data [${edges.size}]"
  }

  private class Edge(val left: Vertex, val right: Vertex) {
    init {
      left.edges.add(this)
      right.edges.add(this)
    }

    fun other(side: Vertex) = if (side == left) right else left

    fun isBetween(a: Vertex, b: Vertex) = (a == left && b == right) || (b == left && a == right)

    fun remove(): List<MutableMap<String, Vertex>> {
      left.edges.remove(this)
      right.edges.remove(this)
      return searchGraphs(listOf(left, right))
    }
  }

  private class Message(
    private val _source: ImmutableNode,
    private val _name: String,
    private val _data: Array<Any?>
  ) : IMessage {
    private var isCanceled = false

    override fun source() = _source
    override fun name() = _name
    override fun data() = _data
    override fun cancel() { isCanceled = true }
  }

  // ----------------------------------------------------------------------- //

  class Wrapper internal constructor(internal val network: Network) : INetwork, Distributor {
    override fun connect(nodeA: ImmutableNode, nodeB: ImmutableNode): Boolean =
      network.connect(nodeA as Node, nodeB as Node)

    override fun disconnect(nodeA: ImmutableNode, nodeB: ImmutableNode): Boolean =
      network.disconnect(nodeA as Node, nodeB as Node)

    override fun remove(node: ImmutableNode): Boolean = network.remove(node as Node)

    override fun node(address: String): ImmutableNode? = network.node(address)

    override fun nodes(): Iterable<ImmutableNode> = network.nodes.toMutableList()

    override fun nodes(reference: ImmutableNode): Iterable<ImmutableNode> =
      network.reachableNodes(reference).toMutableList()

    override fun neighbors(node: ImmutableNode): Iterable<ImmutableNode> =
      network.neighbors(node).toMutableList()

    override fun sendToAddress(source: ImmutableNode, target: String, name: String, vararg data: Any?) =
      network.sendToAddress(source, target, name, *data)

    override fun sendToNeighbors(source: ImmutableNode, name: String, vararg data: Any?) =
      network.sendToNeighbors(source, name, *data)

    override fun sendToReachable(source: ImmutableNode, name: String, vararg data: Any?) =
      network.sendToReachable(source, name, *data)

    override fun sendToVisible(source: ImmutableNode, name: String, vararg data: Any?) =
      network.sendToVisible(source, name, *data)

    override var globalBuffer: Double
      get() = network.globalBuffer
      set(value) { network.globalBuffer = value }

    override var globalBufferSize: Double
      get() = network.globalBufferSize
      set(value) { network.globalBufferSize = value }

    override fun addConnector(connector: ApiConnector) = network.addConnector(connector)

    override fun removeConnector(connector: ApiConnector) = network.removeConnector(connector)

    override fun changeBuffer(delta: Double) = network.changeBuffer(delta)
  }

  companion object {
    private fun searchGraphs(seeds: List<Vertex>): List<MutableMap<String, Vertex>> {
      val seen = mutableSetOf<Vertex>()
      return seeds.mapNotNull { seed ->
        if (seen.contains(seed)) {
          null
        } else {
          val addressed = mutableMapOf<String, Vertex>()
          val queue = ArrayDeque<Vertex>()
          queue.add(seed)
          while (queue.isNotEmpty()) {
            val node = queue.poll()
            seen.add(node)
            addressed[node.data.address!!] = node
            for (edge in node.edges) {
              val other = edge.other(node)
              if (!seen.contains(other) && !queue.contains(other)) {
                queue.add(other)
              }
            }
          }
          addressed
        }
      }
    }
  }
}

object NetworkObject : NetworkAPI {
  override fun joinOrCreateNetwork(world: IBlockAccess, pos: BlockPos) {
    val tileEntity = world.getTileEntity(pos)
    if (tileEntity != null && !tileEntity.isInvalid && tileEntity.world != null && !tileEntity.world.isRemote) {
      for (side in EnumFacing.values()) {
        val npos = tileEntity.pos.offset(side)
        if (tileEntity.world.isBlockLoaded(npos)) {
          val localNode = getNetworkNode(tileEntity, side)
          val neighborTileEntity = tileEntity.world.getTileEntity(npos)
          val neighborNode = getNetworkNode(neighborTileEntity, side.opposite)
          if (localNode is Node) {
            if (neighborNode is Node && neighborNode != localNode && neighborNode.network != null) {
              val canConnectColor = canConnectBasedOnColor(tileEntity, neighborTileEntity!!)
              val canConnectIM = canConnectFromSideIM(tileEntity, side) && canConnectFromSideIM(neighborTileEntity, side.opposite)
              if (canConnectColor && canConnectIM) {
                neighborNode.connect(localNode)
              } else {
                localNode.disconnect(neighborNode)
              }
            }
            if (localNode.network == null) {
              joinNewNetwork(localNode)
            }
          }
        }
      }
    }
  }

  override fun joinOrCreateNetwork(tileEntity: TileEntity?) {
    if (tileEntity != null) {
      val world = tileEntity.world
      val pos = tileEntity.pos
      if (world != null && pos != null) {
        joinOrCreateNetwork(world, pos)
      }
    }
  }

  override fun joinNewNetwork(node: ImmutableNode) {
    if (node is Node && node.network == null) {
      Network(node)
    }
  }

  fun getNetworkNode(tileEntity: TileEntity?, side: EnumFacing): ImmutableNode? {
    if (tileEntity != null) {
      if (tileEntity.hasCapability(Capabilities.SidedEnvironmentCapability, side)) {
        val host = tileEntity.getCapability(Capabilities.SidedEnvironmentCapability, side)
        if (host != null) return host.sidedNode(side)
      }

      if (tileEntity.hasCapability(Capabilities.EnvironmentCapability, side)) {
        val host = tileEntity.getCapability(Capabilities.EnvironmentCapability, side)
        if (host != null) return host.node()
      }
    }

    return null
  }

  private fun getConnectionColor(tileEntity: TileEntity?): UInt {
    if (tileEntity != null) {
      if (tileEntity.hasCapability(Capabilities.ColoredCapability, null)) {
        val colored = tileEntity.getCapability(Capabilities.ColoredCapability, null)
        if (colored != null && colored.controlsConnectivity()) return colored.color.toUInt()
      }
    }

    return Color.rgbValues(EnumDyeColor.SILVER)
  }

  private fun canConnectBasedOnColor(te1: TileEntity, te2: TileEntity): Boolean {
    val c1 = getConnectionColor(te1)
    val c2 = getConnectionColor(te2)
    return c1 == c2 || c1 == Color.rgbValues(EnumDyeColor.SILVER) || c2 == Color.rgbValues(EnumDyeColor.SILVER)
  }

  private fun canConnectFromSideIM(tileEntity: TileEntity?, side: EnumFacing): Boolean {
    return if (tileEntity is ImmibisMicroblock) {
      tileEntity.ImmibisMicroblocks_isSideOpen(side.ordinal)
    } else {
      true
    }
  }

  // ----------------------------------------------------------------------- //

  override fun joinWirelessNetwork(endpoint: WirelessEndpoint) {
    WirelessNetwork.add(endpoint)
  }

  override fun updateWirelessNetwork(endpoint: WirelessEndpoint) {
    WirelessNetwork.update(endpoint)
  }

  override fun leaveWirelessNetwork(endpoint: WirelessEndpoint) {
    WirelessNetwork.remove(endpoint)
  }

  override fun leaveWirelessNetwork(endpoint: WirelessEndpoint, dimension: Int) {
    WirelessNetwork.remove(endpoint, dimension)
  }

  // ----------------------------------------------------------------------- //

  override fun sendWirelessPacket(source: WirelessEndpoint, strength: Double, packet: IPacket) {
    for (endpoint in WirelessNetwork.computeReachableFrom(source, strength)) {
      endpoint.receivePacket(packet, source)
    }
  }

  // ----------------------------------------------------------------------- //

  override fun newNode(host: Environment, reachability: Visibility): NodeBuilder = NodeBuilder(host, reachability)

  override fun newPacket(source: String, destination: String?, port: Int, data: Array<Any?>): IPacket {
    val packet = Packet(source, destination, port, data)
    // We do the size check here instead of in the constructor of the packet
    // itself to avoid errors when loading packets.
    if (packet.size > Settings.get.maxNetworkPacketSize) {
      throw IllegalArgumentException("packet too big (max ${Settings.get.maxNetworkPacketSize})")
    }
    return packet
  }

  override fun newPacket(nbt: NBTTagCompound): IPacket {
    val source = nbt.getString("source")
    val destination = if (!nbt.hasKey("dest")) null else nbt.getString("dest")
    val port = nbt.getInteger("port")
    val ttl = nbt.getInteger("ttl")
    val data = Array<Any?>(nbt.getInteger("dataLength")) { i ->
      if (nbt.hasKey("data$i")) {
        when (val tag = nbt.getTag("data$i")) {
          is NBTTagByte -> java.lang.Boolean.valueOf(tag.byte == 1.toByte())
          is NBTTagShort -> java.lang.Short.valueOf(tag.short)
          is NBTTagInt -> java.lang.Integer.valueOf(tag.int)
          is NBTTagLong -> java.lang.Long.valueOf(tag.long)
          is NBTTagFloat -> java.lang.Float.valueOf(tag.float)
          is NBTTagDouble -> java.lang.Double.valueOf(tag.double)
          is NBTTagString -> tag.string
          is NBTTagByteArray -> tag.byteArray
          else -> null
        }
      } else {
        null
      }
    }
    return Packet(source, destination, port, data, ttl)
  }

  var isServer: () -> Boolean = SideTracker::isServer

  class NodeBuilder(private val _host: Environment, private val _reachability: Visibility) : Builder.NodeBuilder {
    override fun withComponent(name: String, visibility: Visibility): Builder.ComponentBuilder =
      ComponentBuilder(_host, _reachability, name, visibility)

    override fun withComponent(name: String): Builder.ComponentBuilder = withComponent(name, _reachability)

    override fun withConnector(bufferSize: Double): Builder.ConnectorBuilder =
      ConnectorBuilder(_host, _reachability, bufferSize)

    override fun withConnector(): Builder.ConnectorBuilder = withConnector(0.0)

    override fun create(): ImmutableNode? = if (isServer()) {
      object : Node, NodeVarargPart {
        override fun host() = _host
        override fun reachability() = _reachability
        override var address: String? = null
        override var network: INetwork? = null
      }
    } else null
  }

  class ComponentBuilder(
    private val _host: Environment,
    private val _reachability: Visibility,
    private val _name: String,
    private val _visibility: Visibility
  ) : Builder.ComponentBuilder {
    override fun withConnector(bufferSize: Double): Builder.ComponentConnectorBuilder =
      ComponentConnectorBuilder(_host, _reachability, _name, _visibility, bufferSize)

    override fun withConnector(): Builder.ComponentConnectorBuilder = withConnector(0.0)

    override fun create(): li.cil.oc.api.network.Component? = if (isServer()) {
      object : Component, NodeVarargPart {
        override fun host() = _host
        override fun reachability() = _reachability
        override val name = _name
        override var _visibility = Visibility.None
        override var address: String? = null
        override var network: INetwork? = null

        private val callbacks by lazy { Component.createCallbacks(host()) }
        private val hosts by lazy { Component.createHosts(host(), callbacks) }

        override fun getCallbacks() = callbacks
        override fun getHosts() = hosts

        init {
          this.setVisibility(_visibility)
        }
      }
    } else null
  }

  class ConnectorBuilder(
    private val _host: Environment,
    private val _reachability: Visibility,
    private val _bufferSize: Double
  ) : Builder.ConnectorBuilder {
    override fun withComponent(name: String, visibility: Visibility): Builder.ComponentConnectorBuilder =
      ComponentConnectorBuilder(_host, _reachability, name, visibility, _bufferSize)

    override fun withComponent(name: String): Builder.ComponentConnectorBuilder = withComponent(name, _reachability)

    override fun create(): li.cil.oc.api.network.Connector? = if (isServer()) {
      object : Connector, NodeVarargPart {
        override fun host() = _host
        override fun reachability() = _reachability
        override var address: String? = null
        override var network: INetwork? = null
        override var localBufferSize = _bufferSize
        override var localBuffer = 0.0
        override var distributor: Distributor? = null
      }
    } else null
  }

  class ComponentConnectorBuilder(
    private val _host: Environment,
    private val _reachability: Visibility,
    private val _name: String,
    private val _visibility: Visibility,
    private val _bufferSize: Double
  ) : Builder.ComponentConnectorBuilder {
    override fun create(): li.cil.oc.api.network.ComponentConnector? = if (isServer()) {
      object : ComponentConnector, NodeVarargPart {
        override fun host() = _host
        override fun reachability() = _reachability
        override val name = _name
        override var _visibility = Visibility.None
        override var address: String? = null
        override var network: INetwork? = null
        override var localBufferSize = _bufferSize
        override var localBuffer = 0.0

        override var distributor: Distributor? = null

        private val callbacks by lazy { Component.createCallbacks(host()) }
        private val hosts by lazy { Component.createHosts(host(), callbacks) }

        override fun getCallbacks() = callbacks
        override fun getHosts() = hosts

        init {
          setVisibility(_visibility)
        }
      }
    } else null
  }

  // ----------------------------------------------------------------------- //

  class Packet(
    private var _source: String,
    private var _destination: String?,
    private var _port: Int,
    private var _data: Array<Any?>,
    private var _ttl: Int = Settings.get.initialNetworkPacketTTL
  ) : IPacket {
    val size: Int = run {
      val values = _data
      if (values.size > Settings.get.maxNetworkPacketParts)
        throw IllegalArgumentException("packet has too many parts")
      values.size * 2 + values.fold(0) { acc, arg ->
        acc + when (arg) {
          null, Unit -> 1
          is Boolean -> 1
          is Byte -> 2 /* FIXME: Bytes are currently sent as shorts */
          is Short -> 2
          is Int -> 4
          is Long -> 8
          is Float -> 4
          is Double -> 8
          is String -> maxOf(arg.length, 1)
          is ByteArray -> maxOf(arg.size, 1)
          else -> throw IllegalArgumentException("unsupported data type: $arg (${arg.javaClass.canonicalName})")
        }
      }
    }
    override fun size(): Int = size

    override fun source() = _source
    override fun destination() = _destination
    override fun port() = _port
    override fun data() = _data
    override fun ttl() = _ttl

    override fun hop(): IPacket = Packet(_source, _destination, _port, _data, _ttl - 1)

    override fun save(nbt: NBTTagCompound) {
      nbt.setString("source", _source)
      if (_destination != null && _destination!!.isNotEmpty()) {
        nbt.setString("dest", _destination)
      }
      nbt.setInteger("port", _port)
      nbt.setInteger("ttl", _ttl)
      nbt.setInteger("dataLength", _data.size)
      for (i in _data.indices) {
        when (val value = _data[i]) {
          null, Unit -> { }
          is Boolean -> nbt.setBoolean("data$i", value)
          is Byte -> nbt.setShort("data$i", value.toShort())
          is Short -> nbt.setShort("data$i", value)
          is Int -> nbt.setInteger("data$i", value)
          is Long -> nbt.setLong("data$i", value)
          is Float -> nbt.setFloat("data$i", value)
          is Double -> nbt.setDouble("data$i", value)
          is String -> nbt.setString("data$i", value)
          is ByteArray -> nbt.setByteArray("data$i", value)
          else -> OpenComputers.log.warn("Unexpected type while saving network packet: ${value.javaClass.name}")
        }
      }
    }

    override fun toString() = "{source = $_source, destination = $_destination, port = $_port, data = [${_data.joinToString(", ")}]}"
  }
}
