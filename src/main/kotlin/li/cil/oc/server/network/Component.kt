package li.cil.oc.server.network

import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Component as NetComponent
import li.cil.oc.api.network.ManagedPeripheral
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.network.Node as ImmutableNode
import li.cil.oc.common.item.data.NodeData
import li.cil.oc.server.driver.CompoundBlockEnvironment
import li.cil.oc.server.driver.Registry
import li.cil.oc.server.machine.ArgumentsImpl
import li.cil.oc.server.machine.Callbacks
import li.cil.oc.server.machine.Callbacks.ComponentCallback
import li.cil.oc.server.machine.Callbacks.PeripheralCallback
import li.cil.oc.server.machine.Machine
import li.cil.oc.util.SideTracker
import net.minecraft.nbt.NBTTagCompound

interface Component : NetComponent, Node {
  val name: String
  override fun name(): String = name

  var _visibility: Visibility
  override fun visibility(): Visibility = _visibility

  fun getCallbacks(): Map<String, Callbacks.Callback>

  fun getHosts(): Map<String, Any?>

  override fun setVisibility(value: Visibility) {
    if (value.ordinal > reachability().ordinal) {
      throw IllegalArgumentException("Trying to set computer visibility to '$value' on a '$name' node with reachability '${reachability()}'. It will be limited to the node's reachability.")
    }
    if (SideTracker.isServer()) {
      if (network != null) {
        when (_visibility) {
          Visibility.Neighbors -> when (value) {
            Visibility.Network -> addTo(reachableNodes())
            Visibility.None -> removeFrom(neighbors())
            else -> { }
          }
          Visibility.Network -> when (value) {
            Visibility.Neighbors -> {
              val neighborSet = neighbors().toSet()
              removeFrom(reachableNodes().filter { !neighborSet.contains(it) })
            }
            Visibility.None -> removeFrom(reachableNodes())
            else -> { }
          }
          Visibility.None -> when (value) {
            Visibility.Neighbors -> addTo(neighbors())
            Visibility.Network -> addTo(reachableNodes())
            else -> { }
          }
        }
      }
      _visibility = value
    }
  }

  override fun canBeSeenFrom(other: ImmutableNode): Boolean = when (_visibility) {
    Visibility.None -> false
    Visibility.Network -> canBeReachedFrom(other)
    Visibility.Neighbors -> isNeighborOf(other)
  }

  private fun addTo(nodes: Iterable<ImmutableNode>) {
    for (node in nodes) {
      val host = node.host()
      if (host is Machine) {
        host.addComponent(this)
      }
    }
  }

  private fun removeFrom(nodes: Iterable<ImmutableNode>) {
    for (node in nodes) {
      val host = node.host()
      if (host is Machine) {
        host.removeComponent(this)
      }
    }
  }

  // ----------------------------------------------------------------------- //

  override fun methods(): java.util.Set<String> = getCallbacks().keys.toMutableSet()

  override fun annotation(method: String): li.cil.oc.api.machine.Callback {
    val callback = getCallbacks()[method]
    return callback?.annotation ?: throw NoSuchMethodException()
  }

  override fun invoke(method: String, context: Context, vararg arguments: Any?): Array<Any?> {
    val callback = getCallbacks()[method]
    if (callback != null) {
      val hostEnv = getHosts()[method]
      if (hostEnv != null) {
        return Registry.convert(callback.apply(hostEnv, context, ArgumentsImpl(arguments.toList())))
      } else {
        throw NoSuchMethodException()
      }
    } else {
      throw NoSuchMethodException()
    }
  }

  // ----------------------------------------------------------------------- //

  override fun load(nbt: NBTTagCompound) {
    super.load(nbt)
    if (nbt.hasKey(NodeData.VisibilityTag)) {
      _visibility = Visibility.values()[nbt.getInteger(NodeData.VisibilityTag)]
    }
  }

  override fun save(nbt: NBTTagCompound) {
    super.save(nbt)
    nbt.setInteger(NodeData.VisibilityTag, _visibility.ordinal)
  }

  companion object {
    fun createCallbacks(host: Any): Map<String, Callbacks.Callback> = Callbacks.apply(host)

    fun createHosts(host: Any, callbacks: Map<String, Callbacks.Callback>): Map<String, Any?> {
      return when (host) {
        is CompoundBlockEnvironment -> {
          callbacks.mapValues { (method, callback) ->
            when (callback) {
              is ComponentCallback -> {
                host.environments.find { (_, environment) ->
                  environment.javaClass == callback.method.declaringClass
                }?.second
              }
              is PeripheralCallback -> {
                host.environments.find { (_, environment) ->
                  environment is ManagedPeripheral && environment.methods().contains(callback.annotation.value())
                }?.second
              }
              else -> null
            }
          }
        }
        else -> callbacks.mapValues { host }
      }
    }
  }
}
