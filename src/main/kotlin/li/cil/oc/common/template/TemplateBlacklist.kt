package li.cil.oc.common.template

import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.IMC
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

object TemplateBlacklist {
    private val TheBlacklist: Array<ItemStack> by lazy { // scnr
        val pattern = """^([^@]+)(?:@(\d+))?$""".toRegex()
        fun parseDescriptor(id: String, meta: Int): ItemStack? {
            val item = Item.REGISTRY.getObject(ResourceLocation(id))
            return if (item == null) {
                OpenComputers.log.warn("Bad assembler blacklist entry '$id', unknown item id.")
                null
            } else {
                ItemStack(item, 1, meta)
            }
        }
        Settings.get.assemblerBlacklist.mapNotNull { entry ->
            val match = pattern.matchEntire(entry)
            when {
                match == null -> {
                    OpenComputers.log.warn("Bad assembler blacklist entry '$entry', invalid format (should be 'id' or 'id@damage').")
                    null
                }
                match.groupValues[2].isEmpty() -> parseDescriptor(match.groupValues[1], 0)
                else -> {
                    try {
                        parseDescriptor(match.groupValues[1], match.groupValues[2].toInt())
                    } catch (e: NumberFormatException) {
                        OpenComputers.log.warn("Bad assembler blacklist entry '${match.groupValues[1]}@${match.groupValues[2]}', invalid damage value.")
                        null
                    }
                }
            }
        }.toTypedArray()
    }

    @JvmStatic
    fun register() {
        IMC.registerAssemblerFilter("li.cil.oc.common.template.TemplateBlacklist.filter")
    }

    @JvmStatic
    fun filter(stack: ItemStack): Boolean {
        return !TheBlacklist.any { it.isItemEqual(stack) }
    }
}
