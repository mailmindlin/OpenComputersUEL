package li.cil.oc.server.network

import com.google.common.base.Strings
import li.cil.oc.OpenComputers
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.network.Node as ImmutableNode
import li.cil.oc.api.network.Network as ApiNetwork
import net.minecraft.nbt.NBTTagCompound

interface Node : ImmutableNode {
  override fun host(): Environment
  override fun reachability(): Visibility
  var address: String?
  override var network: ApiNetwork?

  override fun canBeReachedFrom(other: ImmutableNode): Boolean = when (reachability()) {
    Visibility.None -> false
    Visibility.Neighbors -> isNeighborOf(other)
    Visibility.Network -> isInSameNetwork(other)
    else -> false
  }

  override fun isNeighborOf(other: ImmutableNode): Boolean =
    isInSameNetwork(other) && network?.neighbors(this)?.any { it == other } == true

  override fun reachableNodes(): java.lang.Iterable<ImmutableNode> =
    network?.nodes(this) ?: emptyList()

  override fun neighbors(): java.lang.Iterable<ImmutableNode> =
    network?.neighbors(this) ?: emptyList()

  // A node should be added to a network before it can connect to a node
  // but, sometimes other mods try to create nodes and connect them before
  // the network is ready. We don't desire those things to crash here.
  // With typical nodes we are talking about components here
  // which will be connected anyways when the network is created
  fun connect(node: ImmutableNode) {
    network?.connect(this, node)
  }

  fun disconnect(node: ImmutableNode) {
    if (network != null && isInSameNetwork(node)) {
      network?.disconnect(this, node)
    }
  }

  override fun remove() {
    network?.remove(this)
  }

  private fun isInSameNetwork(other: ImmutableNode?): Boolean =
    network != null && other != null && network == other.network()

  // ----------------------------------------------------------------------- //

  fun onConnect(node: ImmutableNode) {
    try {
      host().onConnect(node)
    } catch (e: Throwable) {
      OpenComputers.log.warn("A component of type '${host().javaClass.name}' threw an error while being connected to the component network.", e)
    }
  }

  fun onDisconnect(node: ImmutableNode) {
    try {
      host().onDisconnect(node)
    } catch (e: Throwable) {
      OpenComputers.log.warn("A component of type '${host().javaClass.name}' threw an error while being disconnected from the component network.", e)
    }
  }

  // ----------------------------------------------------------------------- //

  fun load(nbt: NBTTagCompound) {
    if (nbt.hasKey("address")) {
      val newAddress = nbt.getString("address")
      if (!Strings.isNullOrEmpty(newAddress) && newAddress != address) {
        val currentNetwork = network
        if (currentNetwork is Network.Wrapper) {
          currentNetwork.network.remap(this, newAddress)
        } else {
          address = newAddress
        }
      }
    }
  }

  fun save(nbt: NBTTagCompound) {
    if (address != null) {
      nbt.setString("address", address)
    }
  }
}

// We have to mixin the vararg methods individually in the actual
// implementations of the different node variants (see Network class) because
// for some reason it fails compiling on Linux otherwise (no clue why).
interface NodeVarargPart : ImmutableNode {
  override fun sendToAddress(target: String, name: String, vararg data: Any?) {
    network?.sendToAddress(this, target, name, *data)
  }

  override fun sendToNeighbors(name: String, vararg data: Any?) {
    network?.sendToNeighbors(this, name, *data)
  }

  override fun sendToReachable(name: String, vararg data: Any?) {
    network?.sendToReachable(this, name, *data)
  }

  override fun sendToVisible(name: String, vararg data: Any?) {
    network?.sendToVisible(this, name, *data)
  }
}
