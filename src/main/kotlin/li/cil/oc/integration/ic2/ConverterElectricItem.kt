package li.cil.oc.integration.ic2

import ic2.api.item.ElectricItem
import ic2.api.item.IElectricItem
import li.cil.oc.api.driver.Converter
import net.minecraft.item.ItemStack

class ConverterElectricItem : Converter {
    override fun convert(value: Any, output: MutableMap<Any, Any>) {
        val stack = value as? ItemStack ?: return
        val electricItem = stack.item as? IElectricItem ?: return

        output["canProvideEnergy"] = electricItem.canProvideEnergy(stack)
        output["charge"] = ElectricItem.manager.getCharge(stack)
        output["maxCharge"] = electricItem.getMaxCharge(stack)
        output["tier"] = electricItem.getTier(stack)
        output["transferLimit"] = electricItem.getTransferLimit(stack)
    }
}
