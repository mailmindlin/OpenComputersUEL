package li.cil.oc.common

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import li.cil.oc.api.network.ManagedEnvironment
import net.minecraft.world.World
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * Keeps track of loaded components by ID. Used to send messages between
 * component representation on server and client without knowledge of their
 * containers. For now this is only used for screens / text buffer components.
 */
abstract class ComponentTracker {
    private val worlds = mutableMapOf<Int, Cache<String, ManagedEnvironment>>()

    private fun components(world: World): Cache<String, ManagedEnvironment> {
        return worlds.getOrPut(world.provider.dimension) {
            CacheBuilder.newBuilder()
                .weakValues()
                .build<String, ManagedEnvironment>()
        }
    }

    @Synchronized
    fun add(world: World, address: String, component: ManagedEnvironment) {
        components(world).put(address, component)
    }

    @Synchronized
    fun remove(world: World, component: ManagedEnvironment) {
        val cache = components(world)
        val keysToInvalidate = cache.asMap().filterValues { it == component }.keys
        cache.invalidateAll(keysToInvalidate)
        cache.cleanUp()
    }

    @Synchronized
    fun get(world: World, address: String): ManagedEnvironment? {
        val cache = components(world)
        cache.cleanUp()
        return cache.getIfPresent(address)
    }

    @SubscribeEvent
    fun onWorldUnload(e: WorldEvent.Unload) {
        clear(e.world)
    }

    @Synchronized
    protected open fun clear(world: World) {
        val cache = components(world)
        cache.invalidateAll()
        cache.cleanUp()
    }
}
