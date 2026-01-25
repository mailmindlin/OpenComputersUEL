package li.cil.oc.util

import com.google.common.base.Charsets
import net.minecraft.item.ItemStack
import net.minecraft.nbt.*
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.util.Constants.NBT

object ExtendedNBT {
    @JvmStatic
    fun toNbt(value: Boolean): NBTTagByte = NBTTagByte(if (value) 1 else 0)

    @JvmStatic
    fun toNbt(value: Byte): NBTTagByte = NBTTagByte(value)

    @JvmStatic
    fun toNbt(value: Short): NBTTagShort = NBTTagShort(value)

    @JvmStatic
    fun toNbt(value: Int): NBTTagInt = NBTTagInt(value)

    @JvmStatic
    fun toNbt(value: Long): NBTTagLong = NBTTagLong(value)

    @JvmStatic
    fun toNbt(value: Float): NBTTagFloat = NBTTagFloat(value)

    @JvmStatic
    fun toNbt(value: Double): NBTTagDouble = NBTTagDouble(value)

    @JvmStatic
    fun toNbt(value: ByteArray): NBTTagByteArray = NBTTagByteArray(value)

    @JvmStatic
    fun toNbt(value: IntArray): NBTTagIntArray = NBTTagIntArray(value)

    @JvmStatic
    fun toNbt(value: BooleanArray): NBTTagByteArray = NBTTagByteArray(value.map { if (it) 1.toByte() else 0.toByte() }.toByteArray())

    @JvmStatic
    fun toNbt(value: String): NBTTagString = NBTTagString(value)

    @JvmStatic
    fun toNbt(value: ItemStack?): NBTTagCompound {
        val nbt = NBTTagCompound()
        value?.writeToNBT(nbt)
        return nbt
    }

    @JvmStatic
    fun toNbt(value: (NBTTagCompound) -> Unit): NBTTagCompound {
        val nbt = NBTTagCompound()
        value(nbt)
        return nbt
    }

    @JvmStatic
    fun toNbt(value: Map<String, *>): NBTTagCompound {
        val nbt = NBTTagCompound()
        for ((key, v) in value) {
            when (v) {
                is Boolean -> nbt.setTag(key, toNbt(v))
                is Byte -> nbt.setTag(key, toNbt(v))
                is Short -> nbt.setTag(key, toNbt(v))
                is Int -> nbt.setTag(key, toNbt(v))
                is Long -> nbt.setTag(key, toNbt(v))
                is Float -> nbt.setTag(key, toNbt(v))
                is Double -> nbt.setTag(key, toNbt(v))
                is ByteArray -> nbt.setTag(key, toNbt(v))
                is IntArray -> nbt.setTag(key, toNbt(v))
                is String -> nbt.setTag(key, toNbt(v))
                is ItemStack -> nbt.setTag(key, toNbt(v))
            }
        }
        return nbt
    }

    @JvmStatic
    fun typedMapToNbt(map: Map<*, *>): NBTBase {
        fun <K, V> mapToList(value: Iterable<Pair<K, V>>): List<V> = value
            .filter { it.first is Number }
            .sortedBy { (it.first as Number).toInt() }
            .map { it.second }

        fun asList(value: Any?): List<*> = when (value) {
            is Array<*> -> value.toList()
            is Map<*, *> -> mapToList(value.map { it.key to it.value })
            is String -> value.toByteArray(Charsets.UTF_8).toList()
            else -> throw IllegalArgumentException("Illegal or missing value.")
        }

        @Suppress("UNCHECKED_CAST")
        fun <K> asMap(value: Any?): Map<K, *> = when (value) {
            is Map<*, *> -> value as Map<K, *>
            else -> throw IllegalArgumentException("Illegal value.")
        }

        val typeAndValue = asMap<String>(map)
        val nbtType = typeAndValue["type"]
        val nbtValue = typeAndValue["value"]

        return when (nbtType) {
            is Number -> when (nbtType.toInt()) {
                NBT.TAG_BYTE -> NBTTagByte(when (nbtValue) {
                    is Number -> nbtValue.toByte()
                    else -> throw IllegalArgumentException("Illegal or missing value.")
                })

                NBT.TAG_SHORT -> NBTTagShort(when (nbtValue) {
                    is Number -> nbtValue.toShort()
                    else -> throw IllegalArgumentException("Illegal or missing value.")
                })

                NBT.TAG_INT -> NBTTagInt(when (nbtValue) {
                    is Number -> nbtValue.toInt()
                    else -> throw IllegalArgumentException("Illegal or missing value.")
                })

                NBT.TAG_LONG -> NBTTagLong(when (nbtValue) {
                    is Number -> nbtValue.toLong()
                    else -> throw IllegalArgumentException("Illegal or missing value.")
                })

                NBT.TAG_FLOAT -> NBTTagFloat(when (nbtValue) {
                    is Number -> nbtValue.toFloat()
                    else -> throw IllegalArgumentException("Illegal or missing value.")
                })

                NBT.TAG_DOUBLE -> NBTTagDouble(when (nbtValue) {
                    is Number -> nbtValue.toDouble()
                    else -> throw IllegalArgumentException("Illegal or missing value.")
                })

                NBT.TAG_BYTE_ARRAY -> NBTTagByteArray(asList(nbtValue).map { v ->
                    when (v) {
                        is Number -> v.toByte()
                        else -> throw IllegalArgumentException("Illegal value.")
                    }
                }.toByteArray())

                NBT.TAG_STRING -> NBTTagString(when (nbtValue) {
                    is String -> nbtValue
                    is ByteArray -> String(nbtValue, Charsets.UTF_8)
                    else -> throw IllegalArgumentException("Illegal or missing value.")
                })

                NBT.TAG_LIST -> {
                    val list = NBTTagList()
                    asList(nbtValue).map { v -> asMap<Any>(v) }.forEach { v -> list.appendTag(typedMapToNbt(v)) }
                    list
                }

                NBT.TAG_COMPOUND -> {
                    val nbt = NBTTagCompound()
                    val values = asMap<String>(nbtValue)
                    for ((name, entry) in values) {
                        try {
                            nbt.setTag(name, typedMapToNbt(asMap<String>(entry)))
                        } catch (t: Throwable) {
                            throw IllegalArgumentException("Error converting entry '$name': ${t.message}")
                        }
                    }
                    nbt
                }

                NBT.TAG_INT_ARRAY -> NBTTagIntArray(asList(nbtValue).map { v ->
                    when (v) {
                        is Number -> v.toInt()
                        else -> throw IllegalArgumentException()
                    }
                }.toIntArray())

                else -> throw IllegalArgumentException("Unsupported NBT type '$nbtType'.")
            }
            else -> {
                if (nbtType != null) throw IllegalArgumentException("Illegal NBT type '$nbtType'.")
                else throw IllegalArgumentException("Missing NBT type.")
            }
        }
    }
}

fun Boolean.toNbt(): NBTTagByte = ExtendedNBT.toNbt(this)
fun Byte.toNbt(): NBTTagByte = ExtendedNBT.toNbt(this)
fun Short.toNbt(): NBTTagShort = ExtendedNBT.toNbt(this)
fun Int.toNbt(): NBTTagInt = ExtendedNBT.toNbt(this)
fun Long.toNbt(): NBTTagLong = ExtendedNBT.toNbt(this)
fun Float.toNbt(): NBTTagFloat = ExtendedNBT.toNbt(this)
fun Double.toNbt(): NBTTagDouble = ExtendedNBT.toNbt(this)
fun ByteArray.toNbt(): NBTTagByteArray = ExtendedNBT.toNbt(this)
fun IntArray.toNbt(): NBTTagIntArray = ExtendedNBT.toNbt(this)
fun BooleanArray.toNbt(): NBTTagByteArray = ExtendedNBT.toNbt(this)
fun String.toNbt(): NBTTagString = ExtendedNBT.toNbt(this)
fun ItemStack.toNbt(): NBTTagCompound = ExtendedNBT.toNbt(this)

fun NBTBase.toTypedMap(): Map<String, Any?> = mapOf(
    "type" to id,
    "value" to when (this) {
        is NBTTagByte -> byte
        is NBTTagShort -> short
        is NBTTagInt -> int
        is NBTTagLong -> long
        is NBTTagFloat -> float
        is NBTTagDouble -> double
        is NBTTagByteArray -> byteArray
        is NBTTagString -> string
        is NBTTagList -> map { entry: NBTBase -> entry.toTypedMap() }
        is NBTTagCompound -> keySet.associateWith { key -> getTag(key).toTypedMap() }
        is NBTTagIntArray -> intArray
        else -> throw IllegalArgumentException()
    }
)

/**
 * Wrapper for chained calls. Allows: nbt.extendedNBT().toTypedMap()
 */
fun NBTTagCompound.extendedNBT(): NBTTagCompound = this

fun NBTTagCompound.setNewCompoundTag(name: String, f: (NBTTagCompound) -> Unit): NBTTagCompound {
    val t = NBTTagCompound()
    f(t)
    setTag(name, t)
    return this
}

fun NBTTagCompound.setNewTagList(name: String, values: Iterable<NBTBase>): NBTTagCompound {
    val t = NBTTagList()
    t.append(values)
    setTag(name, t)
    return this
}

fun NBTTagCompound.setNewTagList(name: String, vararg values: NBTBase): NBTTagCompound =
    setNewTagList(name, values.toList())

fun NBTTagCompound.setNewStringList(name: String, values: Iterable<String>): NBTTagCompound
     = setNewTagList(name, values.map { it.toNbt() })

fun NBTTagCompound.getDirection(name: String): EnumFacing? {
    val id = getByte(name).toInt()
    return if (id < 0 || id > EnumFacing.values().size) null
    else EnumFacing.byIndex(id)
}

fun NBTTagCompound.setDirection(name: String, d: EnumFacing?) {
    when (d) {
        null -> setByte(name, -1)
        else -> setByte(name, d.ordinal.toByte())
    }
}

fun NBTTagCompound.getBooleanArray(name: String): BooleanArray =
    getByteArray(name).map { it == 1.toByte() }.toBooleanArray()

fun NBTTagCompound.setBooleanArray(name: String, value: BooleanArray) =
    setTag(name, ExtendedNBT.toNbt(value))

fun NBTTagList.appendNewCompoundTag(f: (NBTTagCompound) -> Unit) {
    val t = NBTTagCompound()
    f(t)
    appendTag(t)
}

fun NBTTagList.append(values: Iterable<NBTBase>) {
    for (value in values) {
        appendTag(value)
    }
}

fun NBTTagList.append(vararg values: NBTBase) = append(values.toList())

inline fun <reified Tag : NBTBase> NBTTagList.forEach(f: (Tag) -> Unit) {
    val iterable = copy() as NBTTagList
    while (iterable.tagCount() > 0) {
        f(iterable.removeTag(0) as Tag)
    }
}

inline fun <reified Tag : NBTBase, Value> NBTTagList.map(f: (Tag) -> Value): List<Value> {
    val iterable = copy() as NBTTagList
    val buffer = mutableListOf<Value>()
    while (iterable.tagCount() > 0) {
        buffer.add(f(iterable.removeTag(0) as Tag))
    }
    return buffer
}

inline fun <reified Tag : NBTBase> NBTTagList.toArray(): Array<Tag> =
    map<Tag, Tag> { it }.toTypedArray()
