package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api.Network as ApiNetwork
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity.traits.BundledRedstoneAware
import li.cil.oc.common.tileentity.traits.RedstoneChangedEventArgs
import li.cil.oc.integration.util.BundledRedstone
import li.cil.oc.server.component.Redstone as RedstoneComponent
import li.cil.oc.server.component.RedstoneSignaller
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.nbt.NBTTagCompound
import li.cil.oc.common.tileentity.traits.Environment as TraitEnvironment
import li.cil.oc.common.tileentity.traits.BundledRedstoneAware as TraitBundledRedstoneAware
import li.cil.oc.common.tileentity.traits.Tickable as TraitTickable

class Redstone : TileEntityBase.TEEnvironmentBase(), TraitBundledRedstoneAware, TraitTickable {
    override val redstoneDelegate: BundledRedstoneAware.Delegate = register(BundledRedstoneAware::Delegate)

    @JvmField
    val instance = if (BundledRedstone.isAvailable) {
        RedstoneComponent.Bundled(this)
    } else {
        RedstoneComponent.Vanilla(this)
    }

    init {
        instance.wakeNeighborsOnly = false
    }

    private val _node: Node? = instance.node()

    @JvmField
    val dummyNode: Node? = if (_node != null) {
        (_node as? Component)?.setVisibility(Visibility.Network)
        this.outputEnabled = true
        ApiNetwork.newNode(this, Visibility.None)!!.create()
    } else {
        null
    }

    override fun node(): Node? = _node

    // ----------------------------------------------------------------------- //

    companion object {
        private val RedstoneTag = Settings.namespace + "redstone"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super<TEEnvironmentBase>.readFromNBTForServer(nbt)
        instance.load(nbt.getCompoundTag(RedstoneTag))
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super<TEEnvironmentBase>.writeToNBTForServer(nbt)
        nbt.setNewCompoundTag(RedstoneTag) { instance.save(it) }
    }

    // ----------------------------------------------------------------------- //

    override fun onRedstoneInputChanged(args: RedstoneChangedEventArgs) {
        super.onRedstoneInputChanged(args)
        if (_node != null && _node.network() != null && dummyNode != null) {
            _node.connect(dummyNode)
            dummyNode.sendToNeighbors("redstone.changed", args)
        }
    }
}
