package li.cil.oc.server.network

import li.cil.oc.Settings
import li.cil.oc.common.tileentity.Waypoint
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.RTree
import net.minecraftforge.event.world.ChunkEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent


object Waypoints {
  val dimensions = mutableMapOf<Int, RTree<Waypoint>>()

  @SubscribeEvent
  fun onWorldUnload(e: WorldEvent.Unload) {
    if (!e.world.isRemote) {
      dimensions.remove(e.world.provider.dimension)
    }
  }

  @SubscribeEvent
  fun onWorldLoad(e: WorldEvent.Load) {
    if (!e.world.isRemote) {
      dimensions.remove(e.world.provider.dimension)
    }
  }

  // Safety clean up, in case some tile entities didn't properly leave the net.
  @SubscribeEvent
  fun onChunkUnload(e: ChunkEvent.Unload) {
    e.chunk.tileEntityMap.values.forEach {
      if (it is Waypoint) {
        remove(it)
      }
    }
  }

  fun add(waypoint: Waypoint) {
    if (waypoint.isInvalid)
      return
    val world = waypoint.world
    if (world == null || world.isRemote)
      return
    dimensions
      .getOrPut(waypoint.dimension) {
        RTree<Waypoint>(Settings.get.rTreeMaxEntries) { waypoint -> Triple(waypoint.x + 0.5, waypoint.y + 0.5, waypoint.z + 0.5) }
      }
      .add(waypoint)
  }

  fun remove(waypoint: Waypoint) {
    val world = waypoint.world ?: return
    if (world.isRemote) return

    dimensions[waypoint.dimension]?.remove(waypoint)
  }

  fun findWaypoints(pos: BlockPosition, range: Double): Iterable<Waypoint> {
    val set = dimensions[pos.world!!.provider.dimension] ?: return emptyList();
    val bounds = pos.bounds.grow(range * 0.5, range * 0.5, range * 0.5)
    return set.query(Triple(bounds.minX, bounds.minY, bounds.minZ), Triple(bounds.maxX, bounds.maxY, bounds.maxZ))
  }

  private val Waypoint.dimension: Int get() = this.world!!.provider.dimension
}
