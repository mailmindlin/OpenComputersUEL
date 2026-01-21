package li.cil.oc.integration.minecraft

import li.cil.oc.api.driver.Converter
import net.minecraftforge.fluids.FluidTankInfo

object ConverterFluidTankInfo : Converter {
    override fun convert(value: Any?, output: MutableMap<Any, Any>) {
        when (value) {
            is FluidTankInfo -> {
                output["capacity"] = value.capacity
                if (value.fluid != null) {
                    ConverterFluidStack.convert(value.fluid, output)
                } else {
                    output["amount"] = 0
                }
            }
        }
    }
}
