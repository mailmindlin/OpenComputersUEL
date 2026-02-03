package li.cil.oc.util

import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ExtendedAABB {
    @JvmStatic
    val unitBounds: AxisAlignedBB get() = AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
}

fun AxisAlignedBB.offset(pos: BlockPos): AxisAlignedBB {
    return AxisAlignedBB(
        minX + pos.x,
        minY + pos.y,
        minZ + pos.z,
        maxX + pos.x,
        maxY + pos.y,
        maxZ + pos.z
    )
}

val AxisAlignedBB.min: Vec3d get() = Vec3d(minX, minY, minZ)

val AxisAlignedBB.max: Vec3d get() = Vec3d(maxX, maxY, maxZ)

val AxisAlignedBB.volume: Int
    get() {
        val sx = ((maxX - minX) * 16).roundToInt()
        val sy = ((maxY - minY) * 16).roundToInt()
        val sz = ((maxZ - minZ) * 16).roundToInt()
        return sx * sy * sz
    }

val AxisAlignedBB.surface: Int
    get() {
        val sx = ((maxX - minX) * 16).roundToInt()
        val sy = ((maxY - minY) * 16).roundToInt()
        val sz = ((maxZ - minZ) * 16).roundToInt()
        return sx * sy * 2 + sx * sz * 2 + sy * sz * 2
    }

fun AxisAlignedBB.rotateTowards(facing: EnumFacing): AxisAlignedBB {
    val count = when (facing) {
        EnumFacing.WEST -> 3
        EnumFacing.NORTH -> 2
        EnumFacing.EAST -> 1
        else -> 0
    }
    return rotateY(count)
}

fun AxisAlignedBB.rotateY(count: Int): AxisAlignedBB {
    var min = Vec3d(minX - 0.5, minY - 0.5, minZ - 0.5)
    var max = Vec3d(maxX - 0.5, maxY - 0.5, maxZ - 0.5)
    min = min.rotateYaw(count * Math.PI.toFloat() * 0.5f)
    max = max.rotateYaw(count * Math.PI.toFloat() * 0.5f)
    return AxisAlignedBB(
        (min(min.x + 0.5, max.x + 0.5) * 32).roundToInt() / 32.0,
        (min(min.y + 0.5, max.y + 0.5) * 32).roundToInt() / 32.0,
        (min(min.z + 0.5, max.z + 0.5) * 32).roundToInt() / 32.0,
        (max(min.x + 0.5, max.x + 0.5) * 32).roundToInt() / 32.0,
        (max(min.y + 0.5, max.y + 0.5) * 32).roundToInt() / 32.0,
        (max(min.z + 0.5, max.z + 0.5) * 32).roundToInt() / 32.0,
    )
}
