package li.cil.oc.integration.minecraft

import li.cil.oc.api
import net.minecraft.nbt.*

object ConverterNBT : api.driver.Converter {
    override fun convert(value: Any?, output: MutableMap<Any, Any>) {
        when (value) {
            is NBTTagCompound -> output["oc:flatten"] = convert(value)
        }
    }

    private fun convert(nbt: NBTBase): Any? = when (nbt) {
        is NBTTagByte -> nbt.byte
        is NBTTagShort -> nbt.short
        is NBTTagInt -> nbt.int
        is NBTTagLong -> nbt.long
        is NBTTagFloat -> nbt.float
        is NBTTagDouble -> nbt.double
        is NBTTagByteArray -> nbt.byteArray
        is NBTTagString -> nbt.string
        is NBTTagList -> {
            val copy = nbt.copy() as NBTTagList
            (0 until copy.tagCount()).map { convert(copy.removeTag(0)) }.toTypedArray()
        }
        is NBTTagCompound -> {
            nbt.keySet.mapNotNull { key ->
                key?.let { it to convert(nbt.getTag(it)) }
            }.toMap()
        }
        is NBTTagIntArray -> nbt.intArray
        else -> null
    }
}
