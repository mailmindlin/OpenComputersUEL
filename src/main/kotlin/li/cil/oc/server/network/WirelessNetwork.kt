package li.cil.oc.server.network

import li.cil.oc.Settings
import li.cil.oc.api.network.WirelessEndpoint
import li.cil.oc.util.*
import net.minecraft.util.math.Vec3d
import net.minecraftforge.event.world.ChunkEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

import kotlin.math.absoluteValue
import kotlin.math.sqrt

val WirelessEndpoint.x: Int inline get() = this.x()
val WirelessEndpoint.y: Int inline get() = this.y()
val WirelessEndpoint.z: Int inline get() = this.z()

object WirelessNetwork {
  private val dimensions = mutableMapOf<Int, RTree<WirelessEndpoint>>()

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
    e.chunk.tileEntityMap.values
      .asSequence()
      .filterIsInstance<WirelessEndpoint>()
      .forEach(this::remove)
  }

  fun add(endpoint: WirelessEndpoint) {
    dimensions
      .getOrPut(endpoint.dimension) {
        RTree(Settings.get.rTreeMaxEntries) { endpoint ->
          Triple(endpoint.x + 0.5, endpoint.y + 0.5, endpoint.z + 0.5)
        }
      }
      .add(endpoint)
  }

  fun update(endpoint: WirelessEndpoint) {
    val tree = dimensions[endpoint.dimension]?: return
    val (x, y, z) = tree[endpoint]?: return
    val dx = (endpoint.x + 0.5 - x).absoluteValue
    val dy = (endpoint.y + 0.5 - y).absoluteValue
    val dz = (endpoint.z + 0.5 - z).absoluteValue
    if (dx > 0.5 || dy > 0.5 || dz > 0.5) {
      tree.remove(endpoint)
      tree.add(endpoint)
    }
  }

  fun remove(endpoint: WirelessEndpoint, dimension: Int) {
    dimensions[dimension]?.remove(endpoint)
  }

  fun remove(endpoint: WirelessEndpoint) {
    dimensions[endpoint.dimension]?.remove(endpoint)
  }

  fun computeReachableFrom(endpoint: WirelessEndpoint, strength: Double): Sequence<WirelessEndpoint> {
    if (!(strength > 0)) return emptySequence()
    val tree = dimensions[endpoint.dimension] ?: return emptySequence()
    val range = strength + 1
    val range_sq = range * range
    return tree.query(offset(endpoint, -range), offset(endpoint, range))
      .asSequence()
      .filter { it != endpoint }
      .map { zipWithSquaredDistance(endpoint, it) }
      .filter { it.second <= range_sq && isUnobstructed(endpoint, strength, it) }
      .map { it.first }
  }

  private val WirelessEndpoint.dimension: Int get() = this.world().provider.dimension

  private fun offset(endpoint: WirelessEndpoint, value: Double): Triple<Double, Double, Double> =
    Triple(endpoint.x + 0.5 + value, endpoint.y + 0.5 + value, endpoint.z + 0.5 + value)

  private fun zipWithSquaredDistance(reference: WirelessEndpoint, endpoint: WirelessEndpoint): Pair<WirelessEndpoint, Int> =
    Pair(endpoint, run {
      val dx = endpoint.x - reference.x
      val dy = endpoint.y - reference.y
      val dz = endpoint.z - reference.z
      dx * dx + dy * dy + dz * dz
    })

  private fun isUnobstructed(reference: WirelessEndpoint, strength: Double, info: Pair<WirelessEndpoint, Int>): Boolean {
    val (endpoint, distanceSq) = info;
    val distance = sqrt(distanceSq.toDouble())
    val gap = distance - 1.0
    if (!(gap > 0.0)) return true
    // If there's some space between the two wireless network cards we try to
    // figure out if the signal might have been obstructed. We do this by
    // taking a few samples (more the further they are apart) and check if we
    // hit a block. For each block hit we subtract its hardness from the
    // surplus strength left after crossing the distance between the two. If
    // we reach a point where the surplus strength does not suffice we block
    // the message.
    val world = endpoint.world()

    val origin = Vec3d(reference.x.toDouble(), reference.y.toDouble(), reference.z.toDouble())
    val target = Vec3d(endpoint.x.toDouble(), endpoint.y.toDouble(), endpoint.z.toDouble())

    // Vector from reference endpoint (sender) to this one (receiver).
    val delta = subtract(target, origin)
    val v = delta.normalize()

    // Get the vectors that are orthogonal to the direction vector.
    val up = if (v.x == 0.0 && v.z == 0.0) {
      assert(v.y != 0.0)
      Vec3d(1.0, 0.0, 0.0)
    } else {
      Vec3d(0.0, 1.0, 0.0)
    }
    val side = crossProduct(v, up)
    val top = crossProduct(v, side)

    // Accumulated obstructions and number of samples.
    var hardness = 0.0
    val samples = sqrt(gap).toInt().coerceAtLeast(1)

    for (i in 0 until samples) {
      val rGap = world.rand.nextDouble() * gap
      // Adding some jitter to avoid only tracking the perfect line between
      // two endpoints when they are diagonal to each other for example.
      val rSide = world.rand.nextInt(3) - 1
      val rTop = world.rand.nextInt(3) - 1
      val x = (origin.x + v.x * rGap + side.x * rSide + top.x * rTop).toInt()
      val y = (origin.y + v.y * rGap + side.y * rSide + top.y * rTop).toInt()
      val z = (origin.z + v.z * rGap + side.z * rSide + top.z * rTop).toInt()
      val blockPos = BlockPosition(x, y, z, world)
      if (world.isBlockLoaded(blockPos)) {
        //TODO: null check?
        hardness += world.getBlockHardness(blockPos)
      }
    }

    // Normalize and scale obstructions:
    hardness *= gap / samples

    // See if we have enough power to overcome the obstructions.
    return strength - gap > hardness
  }

  private fun subtract(v1: Vec3d, v2: Vec3d) = Vec3d(v1.x - v2.x, v1.y - v2.y, v1.z - v2.z)
  private fun crossProduct(v1: Vec3d, v2: Vec3d) = Vec3d(v1.y * v2.z - v1.z * v2.y, v1.z * v2.x - v1.x * v2.z, v1.x * v2.y - v1.y * v2.x)
}
