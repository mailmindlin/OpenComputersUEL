package li.cil.oc.common.tileentity.traits

import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.tileentity.behaviors.Behavior
import li.cil.oc.api.internal.Rotatable as InternalRotatable
import li.cil.oc.server.PacketSender
import li.cil.oc.util.ExtendedWorld.extendedWorld
import li.cil.oc.util.RotationHelper
import li.cil.oc.util.getRotation
import net.minecraft.entity.Entity
import net.minecraft.util.EnumFacing

/**
 * Interface for tile entities that can be rotated.
 * Implementations must provide a RotatableDelegate instance.
 */
interface Rotatable : TileEntityTrait, RotationAware, InternalRotatable {
    val rotatableDelegate: RotatableDelegate

    var pitch: EnumFacing?
        get() = rotatableDelegate.pitch
        set(value) { rotatableDelegate.pitch = value }
    var yaw: EnumFacing?
        get() = rotatableDelegate.yaw
        set(value) { rotatableDelegate.yaw = value }

    val validFacings: Array<EnumFacing> get() = rotatableDelegate.validFacings

    override fun facing(): EnumFacing? = rotatableDelegate.facing

    override fun toLocal(value: EnumFacing): EnumFacing = rotatableDelegate.toLocal(value)!!
    override fun toGlobal(value: EnumFacing): EnumFacing = rotatableDelegate.toGlobal(value)!!

    fun setFromEntityPitchAndYaw(entity: Entity): Boolean = rotatableDelegate.setFromEntityPitchAndYaw(entity)
    fun setFromFacing(value: EnumFacing): Boolean = rotatableDelegate.setFromFacing(value)
    fun invertRotation(): Boolean = rotatableDelegate.invertRotation()
    fun rotate(axis: EnumFacing): Boolean = rotatableDelegate.rotate(axis)

    fun onRotationChanged() {}

    /**
     * Delegate class that handles rotation state for tile entities.
     * Used by the Rotatable interface to avoid state in interfaces.
     */
    open class RotatableDelegate(
        private val tileEntity: Rotatable,
        val validFacings: Array<EnumFacing> = arrayOf(EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST)
    ): Behavior {
        companion object {
            private val pitch2Direction = arrayOf(EnumFacing.UP, EnumFacing.NORTH, EnumFacing.DOWN)
            private val yaw2Direction = arrayOf(EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.EAST)
        }

        open var pitch: EnumFacing?
            get() {
                val world = tileEntity.world ?: return null
                val pos = tileEntity.pos
                if (!world.isBlockLoaded(pos)) return null
                val state = world.getBlockState(pos)
                return if (state.properties.containsKey(PropertyRotatable.Pitch)) {
                    state.getValue(PropertyRotatable.Pitch)
                } else {
                    EnumFacing.NORTH
                }
            }
            set(value) {
                trySetPitchYaw(when (value) {
                    EnumFacing.DOWN, EnumFacing.UP -> value
                    else -> EnumFacing.NORTH
                }, yaw)
            }
        open var yaw: EnumFacing?
            get() {
                val world = tileEntity.world ?: return null
                val pos = tileEntity.pos
                if (!world.isBlockLoaded(pos)) return null
                val state = world.getBlockState(pos)
                return when {
                    state.properties.containsKey(PropertyRotatable.Yaw) -> state.getValue(PropertyRotatable.Yaw)
                    state.properties.containsKey(PropertyRotatable.Facing) -> state.getValue(PropertyRotatable.Facing)
                    else -> EnumFacing.SOUTH
                }
            }
            set(value: EnumFacing?) {
                trySetPitchYaw(
                    pitch,
                    when (value) {
                        EnumFacing.DOWN, EnumFacing.UP -> yaw
                        else -> value
                    }
                )
            }

        val facing: EnumFacing?
            get() = when (pitch) {
                EnumFacing.DOWN, EnumFacing.UP -> pitch
                else -> yaw
            }

        fun toLocal(value: EnumFacing): EnumFacing? {
            val p = pitch ?: return null
            val y = yaw ?: return null
            return RotationHelper.toLocal(p, y, value)
        }

        fun toGlobal(value: EnumFacing): EnumFacing? {
            val p = pitch ?: return null
            val y = yaw ?: return null
            return RotationHelper.toGlobal(p, y, value)
        }

        fun setFromEntityPitchAndYaw(entity: Entity): Boolean {
            val newPitch = pitch2Direction[(entity.rotationPitch / 90).toInt() + 1]
            val newYaw = yaw2Direction[(entity.rotationYaw / 360 * 4).toInt() and 3]
            return trySetPitchYaw(newPitch, newYaw)
        }

        fun setFromFacing(value: EnumFacing): Boolean = when (value) {
            EnumFacing.DOWN, EnumFacing.UP -> trySetPitchYaw(value, yaw)
            else -> trySetPitchYaw(EnumFacing.NORTH, value)
        }

        fun invertRotation(): Boolean =
            trySetPitchYaw(
                when (pitch) {
                    EnumFacing.DOWN, EnumFacing.UP -> pitch?.opposite
                    else -> EnumFacing.NORTH
                },
                yaw?.opposite
            )

        /** Rotate this tile about `axis` */
        fun rotate(axis: EnumFacing): Boolean {
            val world = tileEntity.world ?: return false
            val block = world.extendedWorld().getBlock(tileEntity.position) ?: return false
            val valid = block.getValidRotations(world, tileEntity.pos) ?: return false
            if (axis !in valid)
                return false

            val currentFacing = facing ?: return false
            val rotated = currentFacing.getRotation(axis)
            val (newPitch, newYaw) = when (rotated) {
                EnumFacing.UP, EnumFacing.DOWN -> {
                    if (rotated == pitch) Pair(rotated, yaw?.getRotation(axis))
                    else Pair(rotated, yaw)
                }
                else -> Pair(EnumFacing.NORTH, rotated)
            }
            return trySetPitchYaw(newPitch, newYaw)
        }

        /** Updates cached translation array and sends notification to clients. */
        protected fun updateTranslation() {
            if (tileEntity.world != null) {
                onRotationChanged()
            }
        }

        open fun trySetPitchYaw(newPitch: EnumFacing?, newYaw: EnumFacing?): Boolean {
            if (newPitch == null || newYaw == null) return false
            val world = tileEntity.world ?: return false
            val pos = tileEntity.pos
            val oldState = world.getBlockState(pos)

            val newState = when {
                oldState.properties.containsKey(PropertyRotatable.Pitch) &&
                        oldState.properties.containsKey(PropertyRotatable.Yaw) ->
                    oldState.withProperty(PropertyRotatable.Pitch, newPitch)
                        .withProperty(PropertyRotatable.Yaw, newYaw)
                oldState.properties.containsKey(PropertyRotatable.Facing) ->
                    oldState.withProperty(PropertyRotatable.Facing, newYaw)
                else -> return false
            }

            if (oldState.hashCode() != newState.hashCode()) {
                world.setBlockState(pos, newState)
                onRotationChanged()
                return true
            }
            return false
        }

        protected open fun onRotationChanged() {
            this.tileEntity.onRotationChanged()
            val world = tileEntity.world ?: return
            if (tileEntity.isServer) {
                PacketSender.sendRotatableState(tileEntity)
            } else {
                world.extendedWorld().notifyBlockUpdate(tileEntity.pos)
            }
            world.notifyNeighborsOfStateChange(tileEntity.pos, world.getBlockState(tileEntity.pos).block, false)
        }
    }

}
