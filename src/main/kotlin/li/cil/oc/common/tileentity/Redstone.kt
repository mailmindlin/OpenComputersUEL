package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.server.component
import li.cil.oc.server.component.RedstoneVanilla
import li.cil.oc.util.ExtendedNBT._
import net.minecraft.nbt.NBTTagCompound

class Redstone : TileEntityBase(), traits.Environment, traits.BundledRedstoneAware, traits.Tickable {
    @JvmField
    val instance: RedstoneVanilla = if (BundledRedstone.isAvailable()) {
        component.Redstone.Bundled(this)
    } else {
        component.Redstone.Vanilla(this)
    }

    init {
        instance.wakeNeighborsOnly = false
    }

    @JvmField
    val node: Component? = instance.node

    @JvmField
    val dummyNode: Node? = if (node != null) {
        node.setVisibility(Visibility.Network)
        _isOutputEnabled = true
        api.Network.newNode(this, Visibility.None).create()
    } else {
        null
    }

    override fun getNode(): Node? = node

    // ----------------------------------------------------------------------- //

    companion object {
        private val RedstoneTag = Settings.namespace + "redstone"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        instance.load(nbt.getCompoundTag(RedstoneTag))
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        nbt.setNewCompoundTag(RedstoneTag) { instance.save(it) }
    }

    // ----------------------------------------------------------------------- //

    override fun onRedstoneInputChanged(args: RedstoneChangedEventArgs) {
        super.onRedstoneInputChanged(args)
        if (node != null && node.network != null && dummyNode != null) {
            node.connect(dummyNode)
            dummyNode.sendToNeighbors("redstone.changed", args)
        }
    }
}
