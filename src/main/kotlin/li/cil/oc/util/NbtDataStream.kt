package li.cil.oc.util

import net.minecraft.nbt.NBTTagCompound
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

object NbtDataStream {
    @JvmStatic
    fun getShortArray(nbt: NBTTagCompound, key: String, array2d: Array<ShortArray>, w: Int, h: Int): Boolean {
        if (!nbt.hasKey(key)) {
            return false
        }

        val rawByteReader = ByteArrayInputStream(nbt.getByteArray(key))
        val memReader = DataInputStream(rawByteReader)
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (2 > memReader.available()) {
                    return true // not great, but get out now
                }
                array2d[y][x] = memReader.readShort()
            }
        }
        return true
    }

    @JvmStatic
    fun getIntArrayLegacy(nbt: NBTTagCompound, key: String, array2d: Array<ShortArray>, w: Int, h: Int): Boolean {
        if (!nbt.hasKey(key)) {
            return false
        }
        // legacy format
        val c = nbt.getIntArray(key)
        for (y in 0 until h) {
            val rowColor = array2d[y]
            for (x in 0 until w) {
                val index = x + y * w
                if (index >= c.size) {
                    return true // not great, but, the read at least started
                }
                rowColor[x] = c[index].toShort()
            }
        }
        return true
    }

    @JvmStatic
    fun setShortArray(nbt: NBTTagCompound, key: String, array: ShortArray) {
        val rawByteWriter = ByteArrayOutputStream()
        val memWriter = DataOutputStream(rawByteWriter)
        array.forEach { memWriter.writeShort(it.toInt()) }
        nbt.setByteArray(key, rawByteWriter.toByteArray())
    }

    @JvmStatic
    fun getOptBoolean(nbt: NBTTagCompound, key: String, df: Boolean): Boolean =
        if (nbt.hasKey(key)) nbt.getBoolean(key) else df

    @JvmStatic
    fun getOptString(nbt: NBTTagCompound, key: String, df: String): String =
        if (nbt.hasKey(key)) nbt.getString(key) else df

    @JvmStatic
    fun getOptNbt(nbt: NBTTagCompound, key: String): NBTTagCompound =
        if (nbt.hasKey(key)) nbt.getCompoundTag(key) else NBTTagCompound()

    @JvmStatic
    fun getOptInt(nbt: NBTTagCompound, key: String, df: Int): Int =
        if (nbt.hasKey(key)) nbt.getInteger(key) else df
}
