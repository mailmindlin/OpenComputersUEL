package li.cil.oc.common.asm.template

import com.google.common.base.Strings
import li.cil.oc.api.Network
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.asm.SimpleComponentTickHandler.Companion.schedule
import li.cil.oc.util.SideTracker
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity

// This class contains actual implementations of methods injected into tile
// entities marked as simple components using the SimpleComponent interface.
// They are called from the template methods, to keep the injected methods
// minimal, instruction wise, and avoid weird dependencies making the injection
// unnecessarily complicated.
internal object StaticSimpleEnvironment {
    private val nodes: MutableMap<Environment, Node?> = mutableMapOf()

    @JvmStatic
    fun node(self: SimpleComponentImpl): Node? {
        // Save ourselves the lookup time in the hash map and avoid mixing in
        // client side tile entities into the map when in single player.
        if (SideTracker.isClient()) {
            return null
        }
        val name = self.componentName
        // If the name is null (or empty) this indicates we don't have a valid
        // component right now, so if we have a node we kill it.
        if (name.isNullOrEmpty()) {
            nodes.remove(self)
                ?.remove()
        } else if (!nodes.containsKey(self)) {
            nodes[self] = Network
                .newNode(self, Visibility.Network)!!
                .withComponent(name)
                .create()!!
        }
        return nodes[self]
    }

    @JvmStatic
    fun validate(self: SimpleComponentImpl) {
        self.validate_OpenComputers()
        schedule(self as TileEntity)
    }

    @JvmStatic
    fun invalidate(self: SimpleComponentImpl) {
        self.invalidate_OpenComputers()
        node(self)?.let { node ->
            node.remove()
            nodes.remove(self)
        }
    }

    @JvmStatic
    fun onChunkUnload(self: SimpleComponentImpl) {
        self.onChunkUnload_OpenComputers()
        node(self)?.let { node ->
            node.remove()
            nodes.remove(self)
        }
    }

    @JvmStatic
    fun readFromNBT(self: SimpleComponentImpl, nbt: NBTTagCompound) {
        self.readFromNBT_OpenComputers(nbt)
        node(self)
            ?.load(nbt.getCompoundTag("oc:node"))
    }

    @JvmStatic
    fun writeToNBT(self: SimpleComponentImpl, nbt: NBTTagCompound): NBTTagCompound {
        val nbt = self.writeToNBT_OpenComputers(nbt)!!
        node(self)?.let { node ->
            val nodeNbt = NBTTagCompound()
            node.save(nodeNbt)
            nbt.setTag("oc:node", nodeNbt)
        }
        return nbt
    }
}
