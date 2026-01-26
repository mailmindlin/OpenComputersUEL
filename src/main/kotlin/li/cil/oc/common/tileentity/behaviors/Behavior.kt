package li.cil.oc.common.tileentity.behaviors

import net.minecraft.util.EnumFacing
import net.minecraftforge.common.capabilities.Capability

/**
 * Base interface for all behavior components.
 *
 * Behaviors are composable components that provide reusable functionality to tile entities.
 * Each behavior manages its own state and lifecycle, and can be mixed freely with other behaviors.
 *
 * Lifecycle Order:
 * 1. Constructor - behaviors are created when tile entity is constructed
 * 2. initialize() - called when tile entity is validated (added to world)
 * 3. update() - called every tick (if tile entity is tickable)
 * 4. dispose() - called when tile entity is invalidated (removed from world)
 *
 * NBT Serialization:
 * - Behaviors are serialized in registration order
 * - Each behavior serializes only its own state
 * - Server and client serialization are separate (different data on each side)
 */
interface Behavior {
    /*/**
     * Called when the tile entity is validated (added to the world).
     * Use this to create server-side objects, join networks, register with systems, etc.
     *
     * @param owner The tile entity that owns this behavior
     */
    fun initialize(owner: TileEntityBase) {}

    /**
     * Called every tick to update behavior state.
     * Only called if the owner tile entity is tickable.
     *
     * @param owner The tile entity that owns this behavior
     */
    fun update(owner: TileEntityBase) {}

    /**
     * Called when the tile entity is invalidated (removed from the world or chunk unloads).
     * Use this to clean up resources, leave networks, unregister from systems, etc.
     *
     * @param owner The tile entity that owns this behavior
     */
    fun dispose(owner: TileEntityBase) {}

    /**
     * Read server-side state from NBT.
     * Called when loading from disk or receiving initial chunk data on client.
     *
     * @param owner The tile entity that owns this behavior
     * @param nbt The NBT tag compound to read from
     */
    fun readFromNBTForServer(owner: TileEntityBase, nbt: NBTTagCompound) {}

    /**
     * Write server-side state to NBT.
     * Called when saving to disk.
     *
     * @param owner The tile entity that owns this behavior
     * @param nbt The NBT tag compound to write to
     */
    fun writeToNBTForServer(owner: TileEntityBase, nbt: NBTTagCompound) {}

    /**
     * Read client-side state from NBT.
     * Called when receiving update packets from server.
     *
     * @param owner The tile entity that owns this behavior
     * @param nbt The NBT tag compound to read from
     */
    fun readFromNBTForClient(owner: TileEntityBase, nbt: NBTTagCompound) {}

    /**
     * Write client-side state to NBT.
     * Called when creating update packets to send to client.
     *
     * @param owner The tile entity that owns this behavior
     * @param nbt The NBT tag compound to write to
     */
    fun writeToNBTForClient(owner: TileEntityBase, nbt: NBTTagCompound) {}*/
}

interface BehaviorUpdate: Behavior {
    fun update()
}

interface BehaviorLifecycle: Behavior {
    fun initialize() {}
    fun dispose() {}
}

interface BehaviorCapability: Behavior {
    fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean = false
    fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? = null
}