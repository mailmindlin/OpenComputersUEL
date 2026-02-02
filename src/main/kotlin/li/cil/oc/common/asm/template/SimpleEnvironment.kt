package li.cil.oc.common.asm.template

import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity

// This is a template implementation of methods injected into classes that are
// marked for component functionality. These methods will be copied into tile
// entities marked as simple components as necessary by the class transformer.
@Suppress("unused")
abstract class SimpleEnvironment : TileEntity(), SimpleComponentImpl {
    override fun node(): Node? = StaticSimpleEnvironment.node(this)

    override fun onConnect(node: Node) {}

    override fun onDisconnect(node: Node) {}

    override fun onMessage(message: Message) {}

    // These are always injected, after possibly existing versions have been
    // renamed to the below variants from the SimpleComponentImpl interface.
    // This allows transparent wrapping of already present implementations,
    // instead of plain overwriting them.
    override fun validate() {
        StaticSimpleEnvironment.validate(this)
    }

    override fun invalidate() {
        StaticSimpleEnvironment.invalidate(this)
    }

    override fun onChunkUnload() {
        StaticSimpleEnvironment.onChunkUnload(this)
    }

    override fun readFromNBT(nbt: NBTTagCompound) {
        StaticSimpleEnvironment.readFromNBT(this, nbt)
    }

    override fun writeToNBT(nbt: NBTTagCompound): NBTTagCompound {
        return StaticSimpleEnvironment.writeToNBT(this, nbt)
    }

    // The following methods are only injected if their real versions do not
    // exist in the class we're injecting into. Otherwise their real versions
    // are renamed to these variations, which simply delegate to the parent.
    // This way they are always guaranteed to be present, so we can simply call
    // them through an interface, and need no runtime reflection.
    override fun validate_OpenComputers() {
        super.validate()
    }

    override fun invalidate_OpenComputers() {
        super.invalidate()
    }

    override fun onChunkUnload_OpenComputers() {
        super.onChunkUnload()
    }

    override fun readFromNBT_OpenComputers(nbt: NBTTagCompound) {
        super.readFromNBT(nbt)
    }

    override fun writeToNBT_OpenComputers(nbt: NBTTagCompound): NBTTagCompound {
        return super.writeToNBT(nbt)
    }
}
