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
            val selector = IMC.getStaticMethod(template.getString("select"), ItemStack::class.java)
            val disassembler = IMC.getStaticMethod(template.getString("disassemble"), ItemStack::class.java, Array<ItemStack>::class.java)

            templates.add(Template(selector, disassembler))
        } catch (t: Throwable) {
            OpenComputers.log.warn("Failed registering disassembler template.", t)
        }
    }

    @JvmStatic
    fun select(stack: ItemStack): Template? = templates.find { it.select(stack) }

    class Template(
        val selector: Method,
        val disassembler: Method
    ) {
        fun select(stack: ItemStack): Boolean = IMC.tryInvokeStatic(selector, stack, false) as Boolean

        fun disassemble(stack: ItemStack, ingredients: Array<ItemStack>): Pair<Array<ItemStack>?, Array<ItemStack>?> {
            return when (val result = IMC.tryInvokeStatic(disassembler, stack, ingredients, null as Array<*>?)) {
                is Array<*> -> when {
                    result.size >= 2 && result[0] is Array<*> && result[1] is Array<*> ->
                        @Suppress("UNCHECKED_CAST")
                        Pair(result[0] as Array<ItemStack>, result[1] as Array<ItemStack>)
                    result.size >= 2 && result[0] is ItemStack && result[1] is Array<*> ->
                        @Suppress("UNCHECKED_CAST")
                        Pair(arrayOf(result[0] as ItemStack), result[1] as Array<ItemStack>)
                    result.size >= 2 && result[0] is Array<*> && result[1] is ItemStack ->
                        @Suppress("UNCHECKED_CAST")
                        Pair(result[0] as Array<ItemStack>, arrayOf(result[1] as ItemStack))
                    result.all { it is ItemStack } ->
                        @Suppress("UNCHECKED_CAST")
                        Pair(result as Array<ItemStack>, null)
                    else -> Pair(null, null)
                }
                else -> Pair(null, null)
            }
        }
    }
}
