package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * @author Vexatos
 */
interface OpenSides : TileEntityTrait {
    val SideCount: Int get() = EnumFacing.VALUES.size
    val defaultState: Boolean get() = false

    var openSides: Array<Boolean> = Array(SideCount) { defaultState }

    fun compressSides(): Byte {
        var result = 0
        for (facing in EnumFacing.values()) {
            if (openSides[facing.ordinal]) {
                result = result or (1 shl facing.ordinal)
            }
        }
        return result.toByte()
    }

    fun uncompressSides(byte: Byte): Array<Boolean> {
        return Array(EnumFacing.values().size) { i ->
            ((1 shl i) and byte.toInt()) != 0
        }
    }

    fun isSideOpen(side: EnumFacing?): Boolean = side != null && openSides[side.ordinal]

    fun setSideOpen(side: EnumFacing?, value: Boolean) {
        if (side != null && openSides[side.ordinal] != value) {
            openSides[side.ordinal] = value
        }
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
