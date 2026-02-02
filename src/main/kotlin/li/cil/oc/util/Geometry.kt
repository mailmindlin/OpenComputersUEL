package li.cil.oc.util

import net.minecraft.util.math.Vec3i

internal data class Vec2i(val x: Int, val z: Int) {
    fun transpose(): Vec2i = Vec2i(z, x)
}

internal inline operator fun Vec3i.component1(): Int = this.x
internal inline operator fun Vec3i.component2(): Int = this.y
internal inline operator fun Vec3i.component3(): Int = this.z