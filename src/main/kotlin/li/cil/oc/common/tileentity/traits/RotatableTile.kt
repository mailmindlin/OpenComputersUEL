package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.common.tileentity.behaviors.NbtSeriailzable
import li.cil.oc.common.tileentity.traits.delegates.RotatableDelegate
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Like Rotatable, but stores the rotation information in the TE's NBT instead
 * of the block's metadata.
 */
interface RotatableTile : Rotatable {
    // ----------------------------------------------------------------------- //
    // State
    // ----------------------------------------------------------------------- //
    override val rotatableDelegate: Delegate

    class Delegate(tile: RotatableTile): RotatableDelegate(tile), NbtSeriailzable {
        /** One of Up, Down and North (where north means forward/no pitch). */
        private var _pitch: EnumFacing = EnumFacing.NORTH

        /** One of the four cardinal directions. */
        private var _yaw: EnumFacing = EnumFacing.SOUTH
        override var pitch: EnumFacing?
            get() = this._pitch
            set(value) { if (value != null) this._pitch = value }
        override var yaw: EnumFacing?
            get() = this._yaw
            set(value) { if (value != null) this._yaw = value }

        companion object {
            private const val PitchTag = Settings.namespace + "pitch"
            private const val YawTag = Settings.namespace + "yaw"
        }

        // ----------------------------------------------------------------------- //

        override fun readFromNBTForServer(nbt: NBTTagCompound) {
            super.readFromNBTForServer(nbt)
            if (nbt.hasKey(PitchTag)) {
                this._pitch = EnumFacing.byIndex(nbt.getInteger(PitchTag))
            }
            if (nbt.hasKey(YawTag)) {
                this._yaw = EnumFacing.byIndex(nbt.getInteger(YawTag))
            }
            validatePitchAndYaw()
        }

        override fun writeToNBTForServer(nbt: NBTTagCompound) {
            super.writeToNBTForServer(nbt)
            nbt.setInteger(PitchTag, _pitch.ordinal)
            nbt.setInteger(YawTag, _yaw.ordinal)
        }

        @SideOnly(Side.CLIENT)
        override fun readFromNBTForClient(nbt: NBTTagCompound) {
            super.readFromNBTForClient(nbt)
            this._pitch = EnumFacing.byIndex(nbt.getInteger(PitchTag))
            this._yaw = EnumFacing.byIndex(nbt.getInteger(YawTag))
            validatePitchAndYaw()
        }

        override fun writeToNBTForClient(nbt: NBTTagCompound) {
            super.writeToNBTForClient(nbt)
            nbt.setInteger(PitchTag, _pitch.ordinal)
            nbt.setInteger(YawTag, _yaw.ordinal)
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
}
