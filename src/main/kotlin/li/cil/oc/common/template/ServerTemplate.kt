package li.cil.oc.common.template

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.common.inventory.ServerInventory
import net.minecraft.item.ItemStack

object ServerTemplate {
    @JvmStatic
    fun selectDisassembler(stack: ItemStack): Boolean =
        api.Items.get(stack) == api.Items.get(Constants.ItemName.ServerTier1) ||
                api.Items.get(stack) == api.Items.get(Constants.ItemName.ServerTier2) ||
                api.Items.get(stack) == api.Items.get(Constants.ItemName.ServerTier3)

    @JvmStatic
    fun disassemble(stack: ItemStack, ingredients: Array<ItemStack>): Array<Array<ItemStack>> {
        val info = object : ServerInventory() {
            override fun getContainer(): ItemStack = stack
        }
        return arrayOf(
            ingredients,
            (0 until info.sizeInventory).map { info.getStackInSlot(it) }.filter { it != null }.toTypedArray()
        )
    }

    @JvmStatic
    fun register() {
        // Disassembler
        api.IMC.registerDisassemblerTemplate(
            "Server",
            "li.cil.oc.common.template.ServerTemplate.selectDisassembler",
            "li.cil.oc.common.template.ServerTemplate.disassemble"
        )
    }
}
