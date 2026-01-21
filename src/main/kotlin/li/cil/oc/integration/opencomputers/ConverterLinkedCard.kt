package li.cil.oc.integration.opencomputers

import java.util.*

import li.cil.oc.Constants
import li.cil.oc.api
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.driver.Converter
import li.cil.oc.server.component
import net.minecraft.item.ItemStack

object ConverterLinkedCard : Converter {
  val linkedCard: ItemInfo by lazy { api.Items.get(Constants.ItemName.LinkedCard) }

  override fun convert(value: Any?, output: MutableMap<Any, Any>) {
    when (value) {
      is ItemStack -> if (api.Items.get(value) == linkedCard) {
        val card = component.LinkedCard()
        output["linkChannel"] = card.tunnel
      }
    }
  }
}
