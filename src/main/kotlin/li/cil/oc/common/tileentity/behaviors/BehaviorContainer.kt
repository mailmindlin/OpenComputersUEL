package li.cil.oc.common.tileentity.behaviors

import li.cil.oc.OpenComputers
import li.cil.oc.common.tileentity.TileEntityBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.capabilities.Capability
import kotlin.reflect.KClass

/**
 * Container that manages all behaviors for a tile entity.
 *
 * Coordinates behavior lifecycle (initialize, update, dispose) and NBT serialization.
 * Behaviors are executed in registration order, ensuring predictable behavior composition.
 *
 * Usage:
 * ```kotlin
 * class MyTileEntity : TileEntityBase() {
 *     private lateinit var networkBehavior: NetworkBehavior
 *     private lateinit var powerBehavior: PowerBehavior
 *
 *     override fun configureBehaviors() {
 *         networkBehavior = behaviors.register(NetworkBehavior { ... })
 *         powerBehavior = behaviors.register(PowerBehavior { ... })
 *     }
 * }
 * ```
 */
class BehaviorContainer(private val owner: TileEntityBase) {

    /**
     * List of all registered behaviors in registration order.
     */
    private val behaviors = mutableListOf<Behavior>()

    private val nbtBehaviors = mutableListOf<NbtSeriailzable>()
    private val updateBehaviors = mutableListOf<BehaviorUpdate>()
    private val lifecycleBehaviors = mutableListOf<BehaviorLifecycle>()
    private val capabilityBehaviors = mutableListOf<BehaviorCapability>()

    /**
     * Map of behavior types to behavior instances for fast lookup.
     */
    private val behaviorsByType = mutableMapOf<KClass<*>, Behavior>()

    /**
     * Whether behaviors have been initialized.
     */
    private var initialized = false

    /**
     * Register a behavior with this container.
     * Behaviors must be registered before initialize() is called.
     *
     * @param behavior The behavior to register
     * @return The same behavior instance (for convenient assignment)
     * @throws IllegalStateException if called after initialization
     */
    fun <T : Behavior> register(behavior: T): T {
        if (initialized)
            throw IllegalStateException("Cannot register behaviors after initialization. Register all behaviors in configureBehaviors().")

        behaviors.add(behavior)
        assert (behavior::class !in behaviorsByType) { "Duplicate behavior type ${behavior::class} in ${owner::class}" }
        behaviorsByType[behavior::class] = behavior
        if (behavior is NbtSeriailzable)
            nbtBehaviors.add(behavior)
        if (behavior is BehaviorUpdate)
            updateBehaviors.add(behavior)
        if (behavior is BehaviorLifecycle)
            lifecycleBehaviors.add(behavior)
        if (behavior is BehaviorCapability)
            capabilityBehaviors.add(behavior)
        return behavior
    }

    /**
     * Get a behavior by type.
     *
     * @param T The behavior type to look up
     * @return The behavior instance, or null if not registered
     */
    private inline fun <reified T : Behavior> get(): T? {
        return behaviorsByType[T::class] as? T
    }

    /**
     * Check if a behavior of the given type is registered.
     *
     * @param T The behavior type to check
     * @return True if a behavior of this type is registered
     */
    private inline fun <reified T : Behavior> has(): Boolean {
        return behaviorsByType.containsKey(T::class)
    }

    /**
     * Initialize all behaviors.
     * Called when the tile entity is validated (added to the world).
     * Behaviors are initialized in registration order.
     */
    fun initialize() {
        if (initialized) {
            // Already initialized, don't initialize again
            // This can happen if validate() is called multiple times
            return
        }

        initialized = true

        for (behavior in lifecycleBehaviors) {
            try {
                behavior.initialize()
            } catch (e: Exception) {
                // Log but continue - don't let one behavior break others
                OpenComputers.log.error("Error initializing behavior ${behavior::class.simpleName} for ${owner::class.simpleName}", e)
            }
        }
    }

    /**
     * Update all behaviors.
     * Called every tick if the tile entity is tickable.
     * Behaviors are updated in registration order.
     */
    fun update() {
        for (behavior in updateBehaviors) {
            try {
                behavior.update()
            } catch (e: Exception) {
                // Log but continue - don't let one behavior break others
                OpenComputers.log.error("Error updating behavior ${behavior::class.simpleName} for ${owner::class.simpleName}", e)
            }
        }
    }

    /**
     * Dispose all behaviors.
     * Called when the tile entity is invalidated (removed from the world).
     * Behaviors are disposed in REVERSE registration order (LIFO cleanup).
     */
    fun dispose() {
        // Dispose in reverse order (last registered, first disposed)
        // This ensures proper cleanup order for dependencies
        for (behavior in lifecycleBehaviors.asReversed()) {
            try {
                behavior.dispose()
            } catch (e: Exception) {
                // Log but continue - don't let one behavior break others
                OpenComputers.log.error("Error disposing behavior ${behavior::class.simpleName} for ${owner::class.simpleName}", e)
            }
        }

        // Clear initialization flag so behaviors can be reinitialized if the tile entity is validated again
        initialized = false
    }

    /**
     * Read server-side state from NBT for all behaviors.
     * Behaviors are read in registration order.
     *
     * @param nbt The NBT tag compound to read from
     */
    fun readFromNBTForServer(nbt: NBTTagCompound) {
        for (behavior in nbtBehaviors) {
            try {
                behavior.readFromNBTForServer(nbt)
            } catch (e: Exception) {
                // Log but continue - don't let one behavior break others
                OpenComputers.log.error("Error reading NBT for behavior ${behavior::class.simpleName} for ${owner::class.simpleName}", e)
            }
        }
    }

    /**
     * Write server-side state to NBT for all behaviors.
     * Behaviors are written in registration order.
     *
     * @param nbt The NBT tag compound to write to
     */
    fun writeToNBTForServer(nbt: NBTTagCompound) {
        for (behavior in nbtBehaviors) {
            try {
                behavior.writeToNBTForServer(nbt)
            } catch (e: Exception) {
                // Log but continue - don't let one behavior break others
                OpenComputers.log.error("Error writing NBT for behavior ${behavior::class.simpleName} for ${owner::class.simpleName}", e)
            }
        }
    }

    /**
     * Read client-side state from NBT for all behaviors.
     * Behaviors are read in registration order.
     *
     * @param nbt The NBT tag compound to read from
     */
    fun readFromNBTForClient(nbt: NBTTagCompound) {
        for (behavior in nbtBehaviors) {
            try {
                behavior.readFromNBTForClient(nbt)
            } catch (e: Exception) {
                // Log but continue - don't let one behavior break others
                OpenComputers.log.error("Error reading client NBT for behavior ${behavior::class.simpleName} for ${owner::class.simpleName}", e)
            }
        }
    }

    /**
     * Write client-side state to NBT for all behaviors.
     * Behaviors are written in registration order.
     *
     * @param nbt The NBT tag compound to write to
     */
    fun writeToNBTForClient(nbt: NBTTagCompound) {
        for (behavior in nbtBehaviors) {
            try {
                behavior.writeToNBTForClient(nbt)
            } catch (e: Exception) {
                // Log but continue - don't let one behavior break others
                OpenComputers.log.error("Error writing client NBT for behavior ${behavior::class.simpleName} for ${owner::class.simpleName}", e)
            }
        }
    }

    /**
     * Get the number of registered behaviors.
     */
    fun size(): Int = behaviors.size

    /**
     * Check if any behaviors are registered.
     */
    fun isEmpty(): Boolean = behaviors.isEmpty()

    /**
     * Check if behaviors are registered.
     */
    fun isNotEmpty(): Boolean = behaviors.isNotEmpty()

    fun hasCapability(capability: Capability<*>, facing: EnumFacing?) = capabilityBehaviors.any { it.hasCapability(capability, facing) }
    fun <T : Any?> getCapability(capability: Capability<T>, facing: EnumFacing?): T? = capabilityBehaviors.firstNotNullOfOrNull { it.getCapability(capability, facing) }
}
