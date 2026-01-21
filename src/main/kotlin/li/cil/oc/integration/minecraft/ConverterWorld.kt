package li.cil.oc.integration.minecraft

import li.cil.oc.api
import net.minecraft.world.World

object ConverterWorld : api.driver.Converter {
    override fun convert(value: Any?, output: MutableMap<Any, Any>) {
        when (value) {
            is World -> output["oc:flatten"] = value.provider
        }
    }
}
