package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Like Rotatable, but stores the rotation information in the TE's NBT instead
 * of the block's metadata.
 */
abstract class RotatableTile : Rotatable() {
    // ----------------------------------------------------------------------- //
    // State
    // ----------------------------------------------------------------------- //

    /** One of Up, Down and North (where north means forward/no pitch). */
    private var _pitch = EnumFacing.NORTH

    /** One of the four cardinal directions. */
    private var _yaw = EnumFacing.SOUTH

    // ----------------------------------------------------------------------- //
    // Accessors
    // ----------------------------------------------------------------------- //

    override val pitch: EnumFacing get() = _pitch

    override fun setPitch(value: EnumFacing) {
        _pitch = value
    }

    override val yaw: EnumFacing get() = _yaw

    override fun setYaw(value: EnumFacing) {
        _yaw = value
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val PitchTag = Settings.namespace + "pitch"
        private val YawTag = Settings.namespace + "yaw"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        if (nbt.hasKey(PitchTag)) {
            setPitch(EnumFacing.byIndex(nbt.getInteger(PitchTag)))
        }
        if (nbt.hasKey(YawTag)) {
            setYaw(EnumFacing.byIndex(nbt.getInteger(YawTag)))
        }
        validatePitchAndYaw()
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        nbt.setInteger(PitchTag, pitch.ordinal)
        nbt.setInteger(YawTag, yaw.ordinal)
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        setPitch(EnumFacing.byIndex(nbt.getInteger(PitchTag)))
        setYaw(EnumFacing.byIndex(nbt.getInteger(YawTag)))
        validatePitchAndYaw()
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        nbt.setInteger(PitchTag, pitch.ordinal)
        nbt.setInteger(YawTag, yaw.ordinal)
    }

    private fun validatePitchAndYaw() {
        if (!_pitch.axis.isVertical) {
            _pitch = EnumFacing.NORTH
        }
        if (!_yaw.axis.isHorizontal) {
            _yaw = EnumFacing.SOUTH
        }
        updateTranslation()
    }

    // ----------------------------------------------------------------------- //

    /** Validates new values against the allowed rotations as set in our block. */
    override fun trySetPitchYaw(pitch: EnumFacing?, yaw: EnumFacing?): Boolean {
        if (pitch == null || yaw == null) return false
        var changed = false
        if (pitch != _pitch) {
            changed = true
            _pitch = pitch
        }
        if (yaw != _yaw) {
            changed = true
            _yaw = yaw
        }
        if (changed) {
            updateTranslation()
        }
        return changed
    }
}
