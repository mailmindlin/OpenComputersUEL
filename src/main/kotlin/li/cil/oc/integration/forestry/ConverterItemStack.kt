package li.cil.oc.integration.forestry

import forestry.api.circuits.ChipsetManager
import forestry.api.circuits.ICircuit
import forestry.api.genetics.AlleleManager
import li.cil.oc.api.driver.Converter
import net.minecraft.item.ItemStack

object ConverterItemStack : Converter {
    override fun convert(value: Any?, output: MutableMap<Any?, Any?>) {
        when {
            value is ItemStack && AlleleManager.alleleRegistry.isIndividual(value) -> {
                output["individual"] = AlleleManager.alleleRegistry.getIndividual(value)
            }
            value is ItemStack && ChipsetManager.circuitRegistry.getCircuitBoard(value) != null -> {
                val cc = ChipsetManager.circuitRegistry.getCircuitBoard(value).circuits
                val names = cc.filterIsInstance<ICircuit>().map { it.uid }.toTypedArray()
                if (names.isNotEmpty()) {
                    output["circuits"] = names
                }
            }
        }
    }
}
