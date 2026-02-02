package li.cil.oc.server.driver

import java.nio.charset.Charset

import com.google.common.hash.Hashing
import li.cil.oc.OpenComputers
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.nbt.NBTTagCompound

class CompoundBlockEnvironment(val name: String, val environments: List<Pair<String, ManagedEnvironment>>) : ManagedEnvironment {
    // Block drivers with visibility < network usually won't make much sense,
    // but let's play it safe and use the least possible visibility based on
    // the drivers we encapsulate.
    private val _node: Component? = ApiNetwork.newNode(this,
        environments.mapNotNull { it.second.node()?.reachability() }.maxOrNull() ?: Visibility.None)!!
        .withComponent(name)
        .create()

    override fun node() = _node

    val updatingEnvironments: List<ManagedEnvironment> = environments.map { it.second }.filter { it.canUpdate() }

    init {
        // Force all wrapped components to be neighbor visible, since we as their
        // only neighbor will take care of all component-related interaction.
        for ((_, environment) in environments) {
            val envNode = environment.node()
            if (envNode is Component) {
                envNode.setVisibility(Visibility.Neighbors)
            }
        }
    }

    override fun canUpdate(): Boolean = environments.any { it.second.canUpdate() }

    override fun update() {
        for (environment in updatingEnvironments) {
            environment.update()
        }
    }

    override fun onMessage(message: Message) {}

    override fun onConnect(node: Node) {
        if (node == this.node()) {
            for ((_, environment) in environments) {
                if (environment.node() != null) {
                    node.connect(environment.node())
                }
            }
        }
    }

    override fun onDisconnect(node: Node) {
        if (node == this.node()) {
            for ((_, environment) in environments) {
                environment.node()?.remove()
            }
        }
    }

    companion object {
      private const val TypeHashTag = "typeHash"
    }

    override fun load(nbt: NBTTagCompound) {
        // Ignore existing data if the underlying type is different.
        if (nbt.hasKey(TypeHashTag) && nbt.getLong(TypeHashTag) != typeHash) return
        node()!!.load(nbt)
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
        node()!!.save(nbt)
        for ((driver, environment) in environments) {
            try {
                nbt.setNewCompoundTag(driver, environment::save)
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
