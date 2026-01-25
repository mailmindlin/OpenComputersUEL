package li.cil.oc.common.template

import li.cil.oc.Constants
import li.cil.oc.api.IMC
import li.cil.oc.api.Items
import li.cil.oc.common.item.data.NavigationUpgradeData
import net.minecraft.item.ItemStack

object NavigationUpgradeTemplate {
    @JvmStatic
    fun selectDisassembler(stack: ItemStack): Boolean = Items.get(stack) == Items.get(Constants.ItemName.NavigationUpgrade)

    @JvmStatic
    fun disassemble(stack: ItemStack, ingredients: Array<ItemStack>): Array<ItemStack> {
        val info = NavigationUpgradeData(stack)
        return ingredients.map { part ->
            if (part.item == net.minecraft.init.Items.FILLED_MAP) info.map else part
        }.toTypedArray()
    }

    @JvmStatic
    fun register() {
        // Disassembler
        IMC.registerDisassemblerTemplate(
            "Navigation Upgrade",
            "li.cil.oc.common.template.NavigationUpgradeTemplate.selectDisassembler",
            "li.cil.oc.common.template.NavigationUpgradeTemplate.disassemble"
        )
    }
}
