package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.network.*
import li.cil.oc.common.EventHandler
import li.cil.oc.common.tileentity.behaviors.Behavior
import li.cil.oc.common.tileentity.behaviors.BehaviorLifecycle
import li.cil.oc.common.tileentity.behaviors.BehaviorUpdate
import li.cil.oc.common.tileentity.behaviors.NbtSeriailzable
import li.cil.oc.util.setNewCompoundTag
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import li.cil.oc.api.network.Environment as ApiEnvironment

/**
 * Abstract base class for tile entities that participate in the OC network.
 * Provides network node lifecycle management and NBT serialization.
 */
interface Environment : TileEntityTrait, ApiEnvironment, EnvironmentHost {
    /**
     * Returns the network node for this environment.
     * Subclasses must implement this to provide their node.
     */
    override fun node(): Node?

    val environmentDelegate: Delegate

    // ----------------------------------------------------------------------- //
    // EnvironmentHost implementation
    // ----------------------------------------------------------------------- //

    override fun world() = world

    override fun xPosition() = x + 0.5

    override fun yPosition() = y + 0.5

    override fun zPosition() = z + 0.5

    override fun markChanged() {
        if (this is Tickable) {
            environmentDelegate.isChangeScheduled = true
        } else {
            world?.markChunkDirty(pos, this.asTileEntity())
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

    class Delegate(val tile: Environment): Behavior, BehaviorUpdate, BehaviorLifecycle, NbtSeriailzable {
        var isChangeScheduled: Boolean = false
        override fun initialize() {
            super.initialize()
            if (tile.isServer) {
                EventHandler.scheduleServer(tile.asTileEntity())
            }
        }

        override fun dispose() {
            if (tile.isServer) {
                tile.node()?.remove()
                if (tile is SidedEnvironment) {
                    for (side in EnumFacing.values()) {
                        tile.sidedNode(side)?.remove()
                    }
                }
            }
        }

        override fun update() {
            //        super.updateEntity()
            if (isChangeScheduled) {
                tile.world?.markChunkDirty(tile.pos, tile.asTileEntity())
                isChangeScheduled = false
            }
        }

        // ----------------------------------------------------------------------- //
        // NBT Serialization
        // ----------------------------------------------------------------------- //

        override fun readFromNBTForServer(nbt: NBTTagCompound) {
//        super.readFromNBTForServer(nbt)
            val n = tile.node()
            if (n != null && n.host() == tile) {
                n.load(nbt.getCompoundTag(NodeTag))
            }
        }

        override fun writeToNBTForServer(nbt: NBTTagCompound) {
//        super.writeToNBTForServer(nbt)
            val n = tile.node()
            if (n != null && n.host() == tile) {
                nbt.setNewCompoundTag(NodeTag) { n.save(it) }
            }
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

//    protected fun result(vararg args: Any?): Array<Any?> = ResultWrapper.result(*args)

    companion object {
        private const val NodeTag = Settings.namespace + "node"
    }
}
