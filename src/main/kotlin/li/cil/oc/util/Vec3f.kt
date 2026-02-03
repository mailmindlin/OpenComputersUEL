package li.cil.oc.util

import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i

data class Vec3f(val x: Float, val y: Float, val z: Float) {
    constructor(pos: Vec3i): this(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())

    fun scale(a: Float): Vec3f = Vec3f(a*x, a*y, a*z)
    fun axpy(m: Float, b: Float): Vec3f = Vec3f(m * x + b, m * y + b, m * z + b)

    operator fun plus(pos: Vec3f): Vec3f
        = Vec3f(this.x + pos.x, this.y + pos.y, this.z + pos.z)
    operator fun minus(pos: Vec3f): Vec3f
        = Vec3f(this.x - pos.x, this.y - pos.y, this.z - pos.z)
    operator fun minus(pos: Vec3i): Vec3f
        = Vec3f(this.x - pos.x.toFloat(), this.y - pos.y.toFloat(), this.z - pos.z.toFloat())
}


internal val EnumFacing.offset: Vec3f
    get() = Vec3f(
        this.xOffset.toFloat(),
        this.yOffset.toFloat(),
        this.zOffset.toFloat(),
    )

internal fun Vec3d.toFloat(): Vec3f
    = Vec3f(
        this.x.toFloat(),
        this.y.toFloat(),
        this.z.toFloat(),
    )