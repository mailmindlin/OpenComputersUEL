package li.cil.oc.common.template

import li.cil.oc.OpenComputers
import li.cil.oc.common.IMC
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import java.lang.reflect.Method

object DisassemblerTemplates {
    private val templates = mutableListOf<Template>()

    @JvmStatic
    fun add(template: NBTTagCompound) {
        try {
            val selector = IMC.getStaticMethod(template.getString("select"), ItemStack::class.java, returnType = Boolean::class.javaPrimitiveType)
            val disassembler = IMC.getStaticMethod(template.getString("disassemble"), ItemStack::class.java, Array<ItemStack>::class.java)

            templates.add(Template(selector, disassembler))
        } catch (t: Throwable) {
            OpenComputers.log.warn("Failed registering disassembler template.", t)
        }
    }

    @JvmStatic
    fun select(stack: ItemStack): Template? = templates.find { it.select(stack) }

    class Template internal constructor(
        private val selector: Method,
        private val disassembler: Method,
    ) {
        fun select(stack: ItemStack): Boolean = IMC.tryInvokeStatic(selector, stack, default = false) as Boolean

        fun disassemble(stack: ItemStack, ingredients: Array<ItemStack>): DisassembleResult? {
            val result = IMC.tryInvokeStatic(disassembler, stack, ingredients, default = null as Array<*>?) ?: return null
            fun tryConvert(r: Any?): Array<out ItemStack>? {
                when (r) {
                    is Array<*> -> r.tryCastTo<ItemStack>()?.let { return it }
                    is ItemStack -> return arrayOf(r)
                }
                return null
            }
            if (result.size >= 2) {
                val (r0, r1) = result
                if (r0 is Array<*> || r1 is Array<*>) {
                    tryConvert(r0)?.let { r0 ->
                        tryConvert(r1)?.let { r1 ->
                            return DisassembleResult(r0, r1)
                        }
                    }
                    // We know that result.tryCastTo<ItemStack>() would fail
                    return null
                }
            }
            result.tryCastTo<ItemStack>()?.let { return DisassembleResult(it, null) }
            return null
        }
    }

    class DisassembleResult(
        val stacks: Array<out ItemStack>,
        val drops: Array<out ItemStack>?,
    )
}

inline fun <reified R> Array<*>.tryCastTo(): Array<out R>? {
    if (this.any { it !is R })
        return null
    // Valid: we validated the elements
    @Suppress("UNCHECKED_CAST")
    return this as Array<out R>
}
