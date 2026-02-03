package li.cil.oc.util

import li.cil.oc.api.machine.Value
import li.cil.oc.util.ResultWrapper.unwrap
import net.minecraft.item.ItemStack

internal object ResultWrapper {
    @JvmStatic
    fun unwrap(arg: Any?): Any? = when (arg) {
        is Number -> arg
        is ItemStack -> if (arg.isEmpty) null else arg
        else -> arg
    }
}

internal typealias Result = Array<out Any?>

fun result(): Result = emptyArray()
/**
 * Extension function to create result arrays for component callbacks.
 * Replaces the Scala implicit conversion from package.scala.
 */
fun result(arg: Unit): Result = arrayOf(null)
fun result(arg: Boolean): Result = arrayOf(arg)
fun result(arg: Int): Result = arrayOf(arg)
fun result(arg: Long): Result = arrayOf(arg)
fun result(arg: String): Result = arrayOf(arg)
fun result(arg: Float): Result = arrayOf(arg)
fun result(arg: Double): Result = arrayOf(arg)
fun result(arg: ByteArray): Result = arrayOf(arg)
fun <K, V> result(arg: Map<K, V>): Result = arrayOf(arg)
fun result(arg: ItemStack): Result = arrayOf(arg.notEmpty())
fun result(arg: Value): Result = arrayOf(arg)
fun result(arg: Any): Result = TODO()
fun result(arg0: Unit, arg1: String): Result = arrayOf(null, arg1)
fun result(vararg args: Any?): Result = args.mapArray(::unwrap)