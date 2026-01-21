package li.cil.oc.common.tileentity.traits

import li.cil.oc.api.internal.Rotatable as InternalRotatable
import li.cil.oc.common.tileentity.traits.delegates.RotatableDelegate
import net.minecraft.entity.Entity
import net.minecraft.util.EnumFacing

/**
 * Interface for tile entities that can be rotated.
 * Implementations must provide a RotatableDelegate instance.
 */
interface Rotatable : TileEntityTrait, RotationAware, InternalRotatable {
    var rotatableDelegate: RotatableDelegate

    var pitch: EnumFacing?
        get() = rotatableDelegate.pitch
        set(value) { rotatableDelegate.pitch = value }
    var yaw: EnumFacing?
        get() = rotatableDelegate.yaw
        set(value) { rotatableDelegate.yaw = value }

    val validFacings: Array<EnumFacing> get() = rotatableDelegate.validFacings

    override fun facing(): EnumFacing? = rotatableDelegate.facing

    override fun toLocal(value: EnumFacing): EnumFacing? = rotatableDelegate.toLocal(value)
    override fun toGlobal(value: EnumFacing): EnumFacing? = rotatableDelegate.toGlobal(value)

    fun setFromEntityPitchAndYaw(entity: Entity): Boolean = rotatableDelegate.setFromEntityPitchAndYaw(entity)
    fun setFromFacing(value: EnumFacing): Boolean = rotatableDelegate.setFromFacing(value)
    fun invertRotation(): Boolean = rotatableDelegate.invertRotation()
    fun rotate(axis: EnumFacing): Boolean = rotatableDelegate.rotate(axis)
}
