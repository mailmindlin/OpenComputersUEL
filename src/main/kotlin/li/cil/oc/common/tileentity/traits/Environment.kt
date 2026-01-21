package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.api.network.Environment as ApiEnvironment
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.common.EventHandler
import li.cil.oc.util.ResultWrapper
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing

/**
 * Abstract base class for tile entities that participate in the OC network.
 * Provides network node lifecycle management and NBT serialization.
 */
interface Environment : TileEntityTrait, ApiEnvironment, EnvironmentHost {
    protected var isChangeScheduled = false

    /**
     * Returns the network node for this environment.
     * Subclasses must implement this to provide their node.
     */
    abstract override fun node(): Node?

    // ----------------------------------------------------------------------- //
    // EnvironmentHost implementation
    // ----------------------------------------------------------------------- //

    override fun world() = world

    override fun xPosition() = x + 0.5

    override fun yPosition() = y + 0.5

    override fun zPosition() = z + 0.5

    override fun markChanged() {
        if (this is Tickable) {
            isChangeScheduled = true
        } else {
            world?.markChunkDirty(pos, this)
        }
    }

    // ----------------------------------------------------------------------- //
    // Connection status
    // ----------------------------------------------------------------------- //

    val isConnected: Boolean
        get() {
            val n = node()
            return n != null && n.address() != null && n.network() != null
        }

    // ----------------------------------------------------------------------- //
    // Lifecycle
    // ----------------------------------------------------------------------- //

    override fun initialize() {
        super.initialize()
        if (isServer) {
            EventHandler.scheduleServer(this)
        }
    }

    override fun updateEntity() {
        super.updateEntity()
        if (isChangeScheduled) {
            world?.markChunkDirty(pos, this)
            isChangeScheduled = false
        }
    }

    override fun dispose() {
        super.dispose()
        if (isServer) {
            node()?.remove()
            if (this is SidedEnvironment) {
                for (side in EnumFacing.values()) {
                    sidedNode(side)?.remove()
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //
    // NBT Serialization
    // ----------------------------------------------------------------------- //

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        val n = node()
        if (n != null && n.host() == this) {
            n.load(nbt.getCompoundTag(NodeTag))
        }
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        val n = node()
        if (n != null && n.host() == this) {
            nbt.setNewCompoundTag(NodeTag) { n.save(it) }
        }
    }

    // ----------------------------------------------------------------------- //
    // Network event handlers (default implementations)
    // ----------------------------------------------------------------------- //

    override fun onMessage(message: Message) {}

    override fun onConnect(node: Node) {}

    override fun onDisconnect(node: Node) {
        if (node == this.node() && node is Connector) {
            // Set it to zero to push all energy into other nodes, to
            // avoid energy loss when removing nodes. Set it back to the
            // original value though, as there are cases where the node
            // is re-used afterwards, without re-adjusting its buffer size.
            val bufferSize = node.localBufferSize()
            node.setLocalBufferSize(0.0)
            node.setLocalBufferSize(bufferSize)
        }
    }

    // ----------------------------------------------------------------------- //
    // Utility
    // ----------------------------------------------------------------------- //

    protected fun result(vararg args: Any?): Array<Any?> = ResultWrapper.result(*args)

    companion object {
        private val NodeTag = Settings.namespace + "node"
    }
}
