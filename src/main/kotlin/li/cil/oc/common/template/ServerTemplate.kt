package li.cil.oc.common.template

import li.cil.oc.Constants
import li.cil.oc.api.IMC
import li.cil.oc.api.Items
import li.cil.oc.common.inventory.ServerInventory
import net.minecraft.item.ItemStack

object ServerTemplate {
    @JvmStatic
    fun selectDisassembler(stack: ItemStack): Boolean =
        Items.get(stack) == Constants.ItemInfo.ServerTier1 ||
                Items.get(stack) == Constants.ItemInfo.ServerTier2 ||
                Items.get(stack) == Constants.ItemInfo.ServerTier3

    @JvmStatic
    fun disassemble(stack: ItemStack, ingredients: Array<ItemStack>): Array<Array<ItemStack>> {
        val info = object : ServerInventory() {
            override val container: ItemStack
                get() = stack
        }
        return arrayOf(
            ingredients,
            (0 until info.sizeInventory).mapNotNull { info.getStackInSlot(it) }.toTypedArray()
        )
    }

    @JvmStatic
    fun register() {
        // Disassembler
        IMC.registerDisassemblerTemplate(
            "Server",
            "li.cil.oc.common.template.ServerTemplate.selectDisassembler",
            "li.cil.oc.common.template.ServerTemplate.disassemble"
        )
    }
}
