package li.cil.oc.integration.minecraft

import com.google.common.hash.Hashing
import li.cil.oc.api
import net.minecraft.world.WorldProvider
import java.util.*

object ConverterWorldProvider : api.driver.Converter {
    override fun convert(value: Any?, output: MutableMap<Any, Any>) {
        when (value) {
            is WorldProvider -> {
                output["id"] = UUID.nameUUIDFromBytes(
                    Hashing.md5().newHasher()
                        .putLong(value.seed)
                        .putInt(value.dimension)
                        .hash().asBytes()
                ).toString()
                output["name"] = value.dimensionType.name
            }
        }
    }
}
