package li.cil.oc.integration.opencomputers

import com.google.common.base.Strings
import li.cil.oc.Constants
import li.cil.oc.api.driver.Converter
import li.cil.oc.common.item.data.NanomachineData
import net.minecraft.item.ItemStack
import li.cil.oc.api.Items as ApiItems

object ConverterNanomachines : Converter {
  override fun convert(value: Any?, output: MutableMap<Any, Any>) {
    when (value) {
      is ItemStack -> if (ApiItems.get(value) == Constants.ItemInfo.Nanomachines) {
        val data = NanomachineData(value)
        if (!Strings.isNullOrEmpty(data.uuid)) {
          output["nanomachines"] = data.uuid
        }
      }
    }
  }
}
