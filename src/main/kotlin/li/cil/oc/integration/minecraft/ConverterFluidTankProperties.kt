package li.cil.oc.integration.minecraft

import li.cil.oc.api
import net.minecraftforge.fluids.capability.IFluidTankProperties

object ConverterFluidTankProperties : api.driver.Converter {
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
