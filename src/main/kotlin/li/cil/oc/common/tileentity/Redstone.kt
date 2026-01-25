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
import li.cil.oc.server.RedstoneComponentVanilla
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.nbt.NBTTagCompound
import li.cil.oc.common.tileentity.traits.Environment as TraitEnvironment
import li.cil.oc.common.tileentity.traits.BundledRedstoneAware as TraitBundledRedstoneAware
import li.cil.oc.common.tileentity.traits.Tickable as TraitTickable

class Redstone : TileEntityBase.TEEnvironmentBase(), TraitBundledRedstoneAware, TraitTickable {
//    override val redstoneDelegate: BundledRedstoneAware.Delegate
    @JvmField
    val instance: RedstoneVanilla = if (BundledRedstone.isAvailable) {
        RedstoneComponent.Bundled(this)
    } else {
        RedstoneComponent.Vanilla(this)
    }

    init {
        instance.wakeNeighborsOnly = false
    }

    @JvmField
    val node: Component? = instance.node

    @JvmField
    val dummyNode: Node? = if (node != null) {
        node.setVisibility(Visibility.Network)
        this.redstoneDelegate.isOutputEnabled = true
        ApiNetwork.newNode(this, Visibility.None).create()
    } else {
        null
    }

    override fun node(): Node? = node

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
        if (node != null && node.network() != null && dummyNode != null) {
            node.connect(dummyNode)
            dummyNode.sendToNeighbors("redstone.changed", args)
        }
    }
}
