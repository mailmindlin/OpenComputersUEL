package li.cil.oc.util

import com.google.common.hash.Hashing
import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.entity.Entity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import kotlin.math.floor

class BlockPosition(val x: Int, val y: Int, val z: Int, val world: World?) {

    constructor(x: Double, y: Double, z: Double, world: World? = null) : this(
        floor(x).toInt(),
        floor(y).toInt(),
        floor(z).toInt(),
        world
    )

    fun offset(direction: EnumFacing, n: Int): BlockPosition = BlockPosition(
        x + direction.xOffset * n,
        y + direction.yOffset * n,
        z + direction.zOffset * n,
        world
    )

    fun offset(direction: EnumFacing): BlockPosition = offset(direction, 1)

    fun offset(x: Double, y: Double, z: Double): Vec3d = Vec3d(this.x + x, this.y + y, this.z + z)

    fun bounds(): AxisAlignedBB = AxisAlignedBB(x.toDouble(), y.toDouble(), z.toDouble(), (x + 1).toDouble(), (y + 1).toDouble(), (z + 1).toDouble())
    val bounds: AxisAlignedBB
        get() = bounds()

    fun toBlockPos(): BlockPos = BlockPos(x, y, z)

    fun toVec3(): Vec3d = Vec3d(x + 0.5, y + 0.5, z + 0.5)

    override fun equals(other: Any?): Boolean {
        if (other is BlockPosition) {
            return other.x == x && other.y == y && other.z == z && other.world == world
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return Hashing
            .goodFastHash(32)
            .newHasher(16)
            .putInt(x)
            .putInt(y)
            .putInt(z)
            .putInt(world?.hashCode() ?: 0)
            .hash()
            .asInt()
    }

    companion object {
        @JvmStatic
        @JvmName("apply")
        operator fun invoke(x: Int, y: Int, z: Int): BlockPosition = BlockPosition(x, y, z, null)

        @JvmStatic
        @JvmName("apply")
        operator fun invoke(x: Double, y: Double, z: Double, world: World): BlockPosition = BlockPosition(x, y, z, world)

        @JvmStatic
        @JvmName("apply")
        operator fun invoke(x: Double, y: Double, z: Double): BlockPosition = BlockPosition(x, y, z, null)

        @JvmStatic
        @JvmName("apply")
        operator fun invoke(v: Vec3d): BlockPosition = BlockPosition(v.x, v.y, v.z, null)

        @JvmStatic
        @JvmName("apply")
        operator fun invoke(v: Vec3d, world: World): BlockPosition = BlockPosition(v.x, v.y, v.z, world)

        @JvmStatic
        @JvmName("apply")
        operator fun invoke(host: EnvironmentHost): BlockPosition = BlockPosition(host.xPosition(), host.yPosition(), host.zPosition(), host.world())

        @JvmStatic
        @JvmName("apply")
        operator fun invoke(entity: Entity): BlockPosition = BlockPosition(entity.posX, entity.posY, entity.posZ, entity.world)

        @JvmStatic
        @JvmName("apply")
        operator fun invoke(pos: BlockPos, world: World): BlockPosition = BlockPosition(pos.x, pos.y, pos.z, world)

        @JvmStatic
        @JvmName("apply")
        operator fun invoke(pos: BlockPos): BlockPosition = BlockPosition(pos.x, pos.y, pos.z, null)
    }
}
