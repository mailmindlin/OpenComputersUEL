package li.cil.oc.integration.minecraft

import li.cil.oc.api.driver.Converter
import net.minecraftforge.fluids.capability.IFluidTankProperties

object ConverterFluidTankProperties : Converter {
    override fun convert(value: Any?, output: MutableMap<Any, Any>) {
        when (value) {
            is IFluidTankProperties -> {
                output["capacity"] = value.capacity
                val fluid = value.contents
                if (fluid != null) {
                    ConverterFluidStack.convert(fluid, output)
                } else {
                    output["amount"] = 0
                }
            }
        }
    }
}
