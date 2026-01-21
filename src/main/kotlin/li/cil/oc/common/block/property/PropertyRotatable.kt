package li.cil.oc.common.block.property

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import net.minecraft.block.properties.PropertyDirection
import net.minecraft.util.EnumFacing

object PropertyRotatable {
    @JvmField
    val Facing: PropertyDirection = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL as Predicate<EnumFacing>)

    @JvmField
    val Pitch: PropertyDirection = PropertyDirection.create("pitch", Predicates.`in`(setOf(EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH)))

    @JvmField
    val Yaw: PropertyDirection = PropertyDirection.create("yaw", EnumFacing.Plane.HORIZONTAL as Predicate<EnumFacing>)
}
