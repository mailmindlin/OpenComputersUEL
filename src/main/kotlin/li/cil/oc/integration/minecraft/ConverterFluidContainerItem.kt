package li.cil.oc.integration.vanilla

import li.cil.oc.api
import net.minecraft.item.ItemStack
import net.minecraftforge.fluids.capability.CapabilityFluidHandler
import net.minecraftforge.fluids.capability.IFluidHandlerItem

object ConverterFluidContainerItem : api.driver.Converter {
    override fun convert(value: Any?, output: MutableMap<Any, Any>) {
        when (value) {
            is ItemStack -> if (value.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) {
                val capability = value.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)
                if (capability is IFluidHandlerItem) {
                    val properties = capability.tankProperties
                    output["capacity"] = properties.sumOf { it.capacity }
                    if (properties.size > 1) {
                        output["fluid"] = properties
                    } else {
                        output["fluid"] = properties[0].contents
                    }
                }
            }
        }
    }
}
