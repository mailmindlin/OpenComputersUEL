package li.cil.oc.util

import net.minecraft.item.ItemStack

object ResultWrapper {
    @JvmStatic
    fun result(vararg args: Any?): Array<Any?> {
        fun unwrap(arg: Any?): Any? = when (arg) {
            is Number -> arg
            is ItemStack -> if (arg.isEmpty) null else arg
            else -> arg
        }
        return args.map { unwrap(it) }.toTypedArray()
    }
}
