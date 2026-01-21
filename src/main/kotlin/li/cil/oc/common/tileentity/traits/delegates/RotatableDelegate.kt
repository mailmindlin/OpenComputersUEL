package li.cil.oc.common.tileentity.traits.delegates

import li.cil.oc.common.block.property.PropertyRotatable
import li.cil.oc.common.tileentity.traits.TileEntityTrait
import li.cil.oc.server.PacketSender
import li.cil.oc.util.ExtendedEnumFacing.extendedEnumFacing
import li.cil.oc.util.ExtendedWorld.extendedWorld
import li.cil.oc.util.RotationHelper
import net.minecraft.entity.Entity
import net.minecraft.util.EnumFacing

/**
 * Delegate class that handles rotation state for tile entities.
 * Used by the Rotatable interface to avoid state in interfaces.
 */
class RotatableDelegate(
    private val tileEntity: TileEntityTrait,
    val validFacings: Array<EnumFacing> = arrayOf(EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST)
) {

    companion object {
        private val pitch2Direction = arrayOf(EnumFacing.UP, EnumFacing.NORTH, EnumFacing.DOWN)
        private val yaw2Direction = arrayOf(EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH, EnumFacing.EAST)
    }

    val pitch: EnumFacing?
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

    val yaw: EnumFacing?
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

    fun setPitch(value: EnumFacing) {
        trySetPitchYaw(
            when (value) {
                EnumFacing.DOWN, EnumFacing.UP -> value
                else -> EnumFacing.NORTH
            },
            yaw
        )
    }

    fun setYaw(value: EnumFacing) {
        trySetPitchYaw(
            pitch,
            when (value) {
                EnumFacing.DOWN, EnumFacing.UP -> yaw
                else -> value
            }
        )
    }

    fun invertRotation(): Boolean =
        trySetPitchYaw(
            when (pitch) {
                EnumFacing.DOWN, EnumFacing.UP -> pitch?.opposite
                else -> EnumFacing.NORTH
            },
            yaw?.opposite
        )

    fun rotate(axis: EnumFacing): Boolean {
        val world = tileEntity.world ?: return false
        val block = world.extendedWorld().getBlock(tileEntity.position) ?: return false
        val valid = block.getValidRotations(world, tileEntity.pos)
        if (valid != null && valid.contains(axis)) {
            val currentFacing = facing ?: return false
            val rotated = currentFacing.extendedEnumFacing().getRotation(axis)
            val (newPitch, newYaw) = when (rotated) {
                EnumFacing.UP, EnumFacing.DOWN -> {
                    if (rotated == pitch) Pair(rotated, yaw?.extendedEnumFacing()?.getRotation(axis))
                    else Pair(rotated, yaw)
                }
                else -> Pair(EnumFacing.NORTH, rotated)
            }
            return trySetPitchYaw(newPitch, newYaw)
        }
        return false
    }

    fun trySetPitchYaw(newPitch: EnumFacing?, newYaw: EnumFacing?): Boolean {
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

    private fun onRotationChanged() {
        val world = tileEntity.world ?: return
        if (tileEntity.isServer) {
            PacketSender.sendRotatableState(tileEntity.asTileEntity())
        } else {
            world.extendedWorld().notifyBlockUpdate(tileEntity.pos)
        }
        world.notifyNeighborsOfStateChange(tileEntity.pos, world.getBlockState(tileEntity.pos).block, false)
    }
}
