package li.cil.oc.integration.opencomputers

import java.util.*

import com.google.common.base.Strings
import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.driver.Converter
import li.cil.oc.common.item.data.NanomachineData
import net.minecraft.item.ItemStack

object ConverterNanomachines : Converter {
  val nanomachines by lazy { ApiItems.get(Constants.ItemName.Nanomachines) }

  override fun convert(value: Any?, output: MutableMap<Any, Any>) {
    when (value) {
      is ItemStack -> if (ApiItems.get(value) == nanomachines) {
        val data = NanomachineData(value)
        if (!Strings.isNullOrEmpty(data.uuid)) {
          output["nanomachines"] = data.uuid
        }
      }
    }
  }
}
