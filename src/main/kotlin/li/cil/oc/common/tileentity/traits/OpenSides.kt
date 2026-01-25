package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.common.tileentity.behaviors.NbtSeriailzable
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * @author Vexatos
 */
interface OpenSides : TileEntityTrait {
    /** Does each side default to open? */
    val defaultState: Boolean get() = false

    val sidesDelegate: Delegate

    class Delegate(private val tile: OpenSides) : NbtSeriailzable {
        var openSides: BooleanArray = BooleanArray(6) { tile.defaultState }

        companion object {
            @JvmStatic
            fun uncompressSides(byte: Byte): BooleanArray {
                val bitset = byte.toInt()
                return BooleanArray(EnumFacing.values().size) { i -> ((1 shl i) and bitset) != 0 }
            }

            @JvmStatic
            fun compressSides(openSides: BooleanArray): Byte {
                assert(openSides.size == EnumFacing.values().size)
                var result = 0
                for (facing in EnumFacing.values()) {
                    if (openSides[facing.ordinal]) {
                        result = result or (1 shl facing.ordinal)
                    }
                }
                return result.toByte()
            }
        }

        internal fun compressSides(): Byte
            = Companion.compressSides(openSides)

        operator fun get(side: EnumFacing?): Boolean = side != null && openSides[side.ordinal]
        operator fun set(side: EnumFacing?, value: Boolean) {
            if (side != null && openSides[side.ordinal] != value) {
                openSides[side.ordinal] = value
            }
        }

        internal fun setCompressed(bits: Byte) {
            this.openSides = uncompressSides(bits)
        }

        override fun readFromNBTForServer(nbt: NBTTagCompound) {
            super.readFromNBTForServer(nbt)
            if (nbt.hasKey(Settings.namespace + "openSides")) {
                openSides = uncompressSides(nbt.getByte(Settings.namespace + "openSides"))
            }
        }

        override fun writeToNBTForServer(nbt: NBTTagCompound) {
            super.writeToNBTForServer(nbt)
            nbt.setByte(Settings.namespace + "openSides", compressSides())
        }

        @SideOnly(Side.CLIENT)
        override fun readFromNBTForClient(nbt: NBTTagCompound) {
            super.readFromNBTForClient(nbt)
            openSides = uncompressSides(nbt.getByte(Settings.namespace + "openSides"))
        }

        override fun writeToNBTForClient(nbt: NBTTagCompound) {
            super.writeToNBTForClient(nbt)
            nbt.setByte(Settings.namespace + "openSides", compressSides())
        }
    }

    fun compressSides(): Byte
        = sidesDelegate.compressSides()

    fun isSideOpen(side: EnumFacing?): Boolean = sidesDelegate[side]

    fun setSideOpen(side: EnumFacing?, value: Boolean) {
        sidesDelegate[side] = value
    }
}
