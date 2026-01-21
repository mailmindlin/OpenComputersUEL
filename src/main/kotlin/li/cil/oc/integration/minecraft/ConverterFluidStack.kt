package li.cil.oc.integration.minecraft

import li.cil.oc.api.driver.Converter
import net.minecraftforge.fluids.FluidStack

object ConverterFluidStack : Converter {
    override fun convert(value: Any?, output: MutableMap<Any, Any>) {
        when (value) {
            is FluidStack -> {
                output["amount"] = value.amount
                output["hasTag"] = value.tag != null
                val fluid = value.fluid
                if (fluid != null) {
                    output["name"] = fluid.name
                    output["label"] = fluid.getLocalizedName(value)
                }
            }
        }
    }
}
