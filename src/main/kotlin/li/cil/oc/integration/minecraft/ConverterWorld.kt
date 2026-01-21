package li.cil.oc.integration.minecraft

import li.cil.oc.api.driver.Converter
import net.minecraft.world.World

object ConverterWorld : Converter {
    override fun convert(value: Any?, output: MutableMap<Any, Any>) {
        when (value) {
            is World -> output["oc:flatten"] = value.provider
        }
    }
}
