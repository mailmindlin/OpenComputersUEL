package li.cil.oc.server.machine

import com.google.common.base.Charsets
import li.cil.oc.api.machine.Arguments
import li.cil.oc.util.ItemUtils
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation

class ArgumentsImpl(private val args: MutableList<Any?>): Arguments {
  override fun iterator() = args.iterator()

  companion object {
    @JvmStatic
    @JvmName("copyFromList")
    operator fun invoke(args: List<Any?>) = ArgumentsImpl(args.toMutableList())
  }

  override fun count() = args.size

  override fun checkAny(index: Int): Any? {
    checkIndex(index, "value")
    return when (val arg = args[index]) {
      Unit, null -> null
      else -> arg
    }
  }

  override fun optAny(index: Int, default: Any) {
    if (!isDefined(index)) default
    else checkAny(index)
  }

  private inline fun <reified T> check(index: Int, name: String): T {
    checkIndex(index, name)
    val value = args[index]
    if (value !is T)
        throw typeError(index, value, name)
    return value
  }

  private inline fun <T> opt(index: Int, default: T, crossinline fetch: (Int) -> T): T =
    if (!isDefined(index)) default
    else fetch(index)

  override fun checkBoolean(index: Int): Boolean = check(index, "boolean")
  override fun checkDouble(index: Int): Double = check<Number>(index, "number").toDouble()
  override fun optBoolean(index: Int, default: Boolean) = opt(index, default, this::checkBoolean)
  override fun optDouble(index: Int, default: Double) = opt(index, default, this::checkDouble)

  private inline fun <T: Number> checkNumber(index: Int, value: Any?, lower: T, upper: T, crossinline conv: Number.() -> T): T {
    return when (value) {
      // TODO: The below is correct behaviour, but breaks existing OC1 code (f.e. file:read(math.huge))
      /* case value: java.lang.Double =>
        if (!java.lang.Double.isFinite(value) || value < java.lang.Integer.MIN_VALUE || value > java.lang.Integer.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.intValue
        }
      case value: java.lang.Float =>
        if (!java.lang.Float.isFinite(value) || value < java.lang.Integer.MIN_VALUE || value > java.lang.Integer.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.intValue
        }
      case value: java.lang.Long =>
        if (value < java.lang.Integer.MIN_VALUE || value > java.lang.Integer.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.intValue
        }
      case value: java.lang.Number => value.intValue
      */
      is java.lang.Double ->
        if (value.isNaN)
          throw intError(index, value)
        else if (value > upper.toDouble())
          upper
        else if (value < lower.toDouble())
          lower
        else
          value.conv()
      is java.lang.Float ->
        if (value.isNaN)
          throw intError(index, value)
        else if (value > upper.toFloat())
          upper
        else if (value < lower.toFloat())
          lower
        else
          value.conv()
      is java.lang.Long ->
        if (value > upper.toLong())
          upper
        else if (value < lower.toLong())
          lower
        else
          value.conv()
      is Number -> value.conv()
      else -> throw typeError(index, value, "integer")
    }
  }

  override fun checkInteger(index: Int): Int {
    checkIndex(index, "integer")
    return checkNumber(index, args[index], Int.MIN_VALUE, Int.MAX_VALUE, Number::toInt)
    /*when (val value = args[index]) {
      // TODO: The below is correct behaviour, but breaks existing OC1 code (f.e. file:read(math.huge))
      /* case value: java.lang.Double =>
        if (!java.lang.Double.isFinite(value) || value < java.lang.Integer.MIN_VALUE || value > java.lang.Integer.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.intValue
        }
      case value: java.lang.Float =>
        if (!java.lang.Float.isFinite(value) || value < java.lang.Integer.MIN_VALUE || value > java.lang.Integer.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.intValue
        }
      case value: java.lang.Long =>
        if (value < java.lang.Integer.MIN_VALUE || value > java.lang.Integer.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.intValue
        }
      case value: java.lang.Number => value.intValue
      */
      is java.lang.Double ->
        if (value.isNaN)
          throw intError(index, value)
        else if (value > Int.MAX_VALUE.toDouble())
          Int.MAX_VALUE
        else if (value < Int.MIN_VALUE.toDouble())
          Int.MIN_VALUE
        else
          value.toInt()
      is java.lang.Float ->
        if (value.isNaN)
          throw intError(index, value)
        else if (value > Int.MAX_VALUE.toFloat())
          Int.MAX_VALUE
        else if (value < Int.MIN_VALUE.toFloat())
          Int.MIN_VALUE
        else
          value.toInt()
      is java.lang.Long ->
        if (value > Int.MAX_VALUE.toLong())
          Int.MAX_VALUE
        else if (value < Int.MIN_VALUE.toLong())
          Int.MIN_VALUE
        else
          value.toInt()
      case value: java.lang.Number => value.intValue
      case value => throw typeError(index, value, "integer")
    }*/
  }

  override fun optInteger(index: Int, default: Int) = opt(index, default, this::checkInteger)

  override fun checkLong(index: Int): Long {
    checkIndex(index, "integer")
    return checkNumber(index, args[index], Long.MIN_VALUE, Long.MAX_VALUE, Number::toLong)
    /*when (val value = args[index]) {
      // TODO: The below is correct behaviour, but breaks existing OC1 code (f.e. file:read(math.huge))
      /* case value: java.lang.Double =>
        if (!java.lang.Double.isFinite(value) || value < java.lang.Long.MIN_VALUE || value > java.lang.Long.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.longValue
        }
      case value: java.lang.Float =>
        if (!java.lang.Float.isFinite(value) || value < java.lang.Long.MIN_VALUE || value > java.lang.Long.MAX_VALUE) {
          throw intError(index, value)
        } else {
          value.longValue
        }
      case value: java.lang.Number => value.longValue
      */
      case value: java.lang.Double =>
        if (value.isNaN)
          throw intError(index, value)
        else if (value > java.lang.Long.MAX_VALUE)
          java.lang.Long.MAX_VALUE
        else if (value < java.lang.Long.MIN_VALUE)
          java.lang.Long.MIN_VALUE
        else
          value.longValue
      case value: java.lang.Float =>
        if (value.isNaN)
          throw intError(index, value)
        else if (value > java.lang.Long.MAX_VALUE)
          java.lang.Long.MAX_VALUE
        else if (value < java.lang.Long.MIN_VALUE)
          java.lang.Long.MIN_VALUE
        else
          value.longValue
      case value: java.lang.Number => value.longValue
      case value => throw typeError(index, value, "integer")
    }*/
  }

  override fun optLong(index: Int, default: Long) = opt(index, default, this::checkLong)

  override fun checkString(index: Int): String {
    checkIndex(index, "string")
    return when (val arg = args[index]) {
      is String -> arg
      is ByteArray -> String(arg, Charsets.UTF_8)
      else -> throw typeError(index, arg, "string")
    }
  }

  override fun optString(index: Int, default: String): String = opt(index, default, this::checkString)

  override fun checkByteArray(index: Int): ByteArray {
    checkIndex(index, "string")
    return when (val arg = args[index]) {
      is String -> arg.toByteArray(Charsets.UTF_8)
      is ByteArray -> arg
      else -> throw typeError(index, arg, "string")
    }
  }

  override fun optByteArray(index: Int, default: ByteArray) = opt(index, default, this::checkByteArray)

  override fun checkTable(index: Int): Map<*, *> {
    checkIndex(index, "table")
    return when (val value = args[index]) {
      is Map<*, *> -> value
      is MutableMap<*, *> -> value
      else -> throw typeError(index, value, "table")
    }
  }

  override fun optTable(index: Int, default: Map<*, *>) = opt(index, default, this::checkTable)

  override fun checkItemStack(index: Int): ItemStack {
    val map = checkTable(index)
    val name = map["name"]
    if (name !is String)
      throw IllegalArgumentException("invalid item stack")

    val damage = when (val damage = map["damage"]) {
      is Number -> damage.toInt()
      else -> 0
    }
    val tag = when (val tag = map["tag"]) {
      is ByteArray -> toNbtTagCompound(tag)
      is String -> toNbtTagCompound(tag.toByteArray(Charsets.UTF_8))
      else -> null
    }
    return makeStack(name, damage, tag)
  }

  override fun optItemStack(index: Int, default: ItemStack)= opt(index, default, this::checkItemStack)

  @OptIn(ExperimentalStdlibApi::class)
  private inline fun <reified T> isType(index: Int): Boolean
    = (index in 0..< this.count()) && (args[index] is T)

  override fun isBoolean(index: Int) = isType<Boolean>(index)
  override fun isDouble(index: Int) = isType<Double>(index)

  override fun isInteger(index: Int) =
    isDefined(index) && when (val value = args[index]) {
      // TODO: The below is correct behaviour, but may break existing OC1 code
      /* case value: java.lang.Double =>
        java.lang.Double.isFinite(value) && value >= java.lang.Integer.MIN_VALUE && value <= java.lang.Integer.MAX_VALUE
      case value: java.lang.Float =>
        java.lang.Float.isFinite(value) && value >= java.lang.Integer.MIN_VALUE && value <= java.lang.Integer.MAX_VALUE
      case value: java.lang.Long =>
        value >= java.lang.Integer.MIN_VALUE && value <= java.lang.Integer.MAX_VALUE */
      is Double -> !value.isNaN()
      is Float -> !value.isNaN()
      is Number -> true
      else -> false
    }

  override fun isLong(index: Int) =
    isDefined(index) && when (val value = args[index]) {
      // TODO: The below is correct behaviour, but may break existing OC1 code
      /* case value: java.lang.Double =>
        java.lang.Double.isFinite(value) && value >= java.lang.Long.MIN_VALUE && value <= java.lang.Long.MAX_VALUE
      case value: java.lang.Float =>
        java.lang.Float.isFinite(value) && value >= java.lang.Long.MIN_VALUE && value <= java.lang.Long.MAX_VALUE */
      is Double -> !value.isNaN()
      is Float -> !value.isNaN()
      is Number -> true
      else -> false
    }

  override fun isString(index: Int) = isDefined(index) && when (args[index]) {
    is String, is ByteArray -> true
    else -> false
  }
  override fun isByteArray(index: Int) = isString(index)

  override fun isTable(index: Int) =
    isDefined(index) && when (val value = args[index]) {
      is Map<*, *> -> true
      else -> false
    }

  override fun isItemStack(index: Int) = isTable(index) && run {
      val map = checkTable(index)
      when (map["name"]) {
        is String, is ByteArray -> true
        else -> false
      }
  }

  override fun toArray() = args.map { arg ->
    when (arg) {
      is ByteArray -> String(arg, Charsets.UTF_8)
      else -> arg
    }
  }.toTypedArray()

  private fun isDefined(index: Int) = index >= 0 && index < args.size && args[index] != null

  private fun checkIndex(index: Int, name: String) =
    if (index < 0) throw IndexOutOfBoundsException()
    else if (args.size <= index) throw IllegalArgumentException("bad arguments #${index + 1} ($name expected, got no value)")
    else Unit

  private fun typeError(index: Int, have: Any?, want: String) = IllegalArgumentException("bad argument #${index + 1} ($want expected, got ${typeName(have)})")
  private fun intError(index: Int, have: Any?) = IllegalArgumentException("bad argument #${index + 1} (${typeName(have)} has no integer representation)")

  private fun typeName(value: Any?): String = when (value) {
    null, Unit -> "nil"
    is Boolean -> "boolean"
    is Byte, is Short, is Integer, is Long-> "integer"
    is Number -> "number"
    is String, is ByteArray -> "string"
    is Map<*, *> -> "table"
    else -> value.javaClass.simpleName
  }

  private fun makeStack(name: String, damage: Int, tag: NBTTagCompound?): ItemStack {
    val item = Item.REGISTRY.getObject(ResourceLocation(name)) ?: throw IllegalArgumentException("invalid item stack")
    val stack = ItemStack(item, 1, damage)
    tag?.let(stack::setTagCompound)
    return stack
  }

  private fun toNbtTagCompound(data: ByteArray) = ItemUtils.loadTag(data)
}
