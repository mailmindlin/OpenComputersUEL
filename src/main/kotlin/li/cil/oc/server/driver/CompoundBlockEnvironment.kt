package li.cil.oc.server.driver

import java.nio.charset.Charset

import com.google.common.hash.Hashing
import li.cil.oc.OpenComputers
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.network._
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Component
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.NBTTagCompound

class CompoundBlockEnvironment(val name: String, val environments: List<Pair<String, ManagedEnvironment>>): ManagedEnvironment {
  // Block drivers with visibility < network usually won't make much sense,
  // but let's play it safe and use the least possible visibility based on
  // the drivers we encapsulate.
  val node: Component = ApiNetwork.newNode(this, (environments.filter(_._2.node != null).map(_._2.node.reachability) ++ Seq(Visibility.None)).max)
    .withComponent(name)
    .create()

  val updatingEnvironments: Seq[ManagedEnvironment] = environments.map(_._2).filter(_.canUpdate)

  // Force all wrapped components to be neighbor visible, since we as their
  // only neighbor will take care of all component-related interaction.
  for ((_, environment) <- environments) environment.node match {
    case component: Component => component.setVisibility(Visibility.Neighbors)
    case _ =>
  }

  override fun canUpdate: Boolean = environments.exists(_._2.canUpdate)

  override fun update() {
    for (environment <- updatingEnvironments) {
      environment.update()
    }
  }

  override fun onMessage(message: Message) {}

  override fun onConnect(node: Node) {
    if (node == this.node) {
      for ((_, environment) in environments if environment.node != null) {
        node.connect(environment.node)
      }
    }
  }

  override fun onDisconnect(node: Node) {
    if (node == this.node) {
      for ((_, environment) in environments if environment.node != null) {
        environment.node.remove()
      }
    }
  }

  private final val TypeHashTag = "typeHash"

  override fun load(nbt: NBTTagCompound) {
    // Ignore existing data if the underlying type is different.
    if (nbt.hasKey(TypeHashTag) && nbt.getLong(TypeHashTag) != typeHash) return
    node.load(nbt)
    for ((driver, environment) in environments) {
      if (nbt.hasKey(driver)) {
        try {
          environment.load(nbt.getCompoundTag(driver))
        } catch (e: Exception) {
          OpenComputers.log.warn("A block component of type '${environment.javaClass.name}' (provided by driver '$driver') threw an error while loading.", e)
        }
      }
    }
  }

  override fun save(nbt: NBTTagCompound) {
    nbt.setLong(TypeHashTag, typeHash)
    node.save(nbt)
    for ((driver, environment) in environments) {
      try {
        nbt.setNewCompoundTag(driver, environment.save)
      } catch (e: Exception) {
        OpenComputers.log.warn("A block component of type '${environment.javaClass.name}' (provided by driver '$driver') threw an error while saving.", e)
      }
    }
  }

  private val typeHash: Long get() {
    val hash = Hashing.sha256().newHasher()
    environments.map { it.second.javaClass.name }
      .sorted()
      .forEach {
        hash.putString(it, Charset.defaultCharset())
      }
    return hash.hash().asLong()
  }
}