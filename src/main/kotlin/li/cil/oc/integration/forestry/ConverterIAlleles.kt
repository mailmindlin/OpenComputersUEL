package li.cil.oc.integration.forestry

import com.google.common.collect.Maps
import forestry.api.genetics.IAlleleSpecies
import forestry.api.genetics.IMutation
import li.cil.oc.api.driver.Converter

class ConverterIAlleles : Converter {
    override fun convert(value: Any, output: MutableMap<Any, Any>) {
        if (value is IMutation) {
            val mutation: IMutation = value

            val allele1 = mutation.allele0
            if (allele1 != null) {
                val alleleMap1: MutableMap<Any, Any> = Maps.newHashMap()
                convert(allele1, alleleMap1)
                output["allele1"] = alleleMap1
            }
            val allele2 = mutation.allele1
            if (allele2 != null) {
                val alleleMap2: MutableMap<Any, Any> = Maps.newHashMap()
                convert(allele2, alleleMap2)
                output["allele2"] = alleleMap2
            }
            output["chance"] = mutation.baseChance
            output["specialConditions"] = mutation.specialConditions.toTypedArray()
        }

        if (value is IAlleleSpecies) {
            convertAlleleSpecies(value, output)
        }
    }

    private fun convertAlleleSpecies(value: IAlleleSpecies, output: MutableMap<Any, Any>) {
        output["name"] = value.alleleName
        output["uid"] = value.uid
    }
}
