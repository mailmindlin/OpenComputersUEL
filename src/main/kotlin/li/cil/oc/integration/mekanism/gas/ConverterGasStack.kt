package li.cil.oc.integration.mekanism.gas

import li.cil.oc.Settings
import li.cil.oc.api.driver.Converter
import mekanism.api.gas.GasStack

object ConverterGasStack : Converter {
    override fun convert(value: Any?, output: MutableMap<Any?, Any?>) {
        when (value) {
            is GasStack -> {
                if (Settings.get.insertIdsInConverters) {
                    output["id"] = value.gas.id
                }
                output["amount"] = value.amount
                val gas = value.gas
                if (gas != null) {
                    output["name"] = gas.name
                    output["label"] = gas.localizedName
                }
            }
        }
    }
}
