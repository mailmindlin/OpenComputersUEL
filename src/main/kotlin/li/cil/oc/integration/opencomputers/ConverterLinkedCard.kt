package li.cil.oc.integration.opencomputers

import java.util.*

import li.cil.oc.Constants
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.api.detail.ItemInfo
import li.cil.oc.api.driver.Converter
import li.cil.oc.server.component.LinkedCard
import net.minecraft.item.ItemStack

object ConverterLinkedCard : Converter {
  val linkedCard: ItemInfo by lazy { Constants.ItemInfo.LinkedCard }

  override fun convert(value: Any?, output: MutableMap<Any, Any>) {
    when (value) {
      is ItemStack -> if (ApiItems.get(value) == linkedCard) {
        val card = LinkedCard()
        output["linkChannel"] = card.tunnel
      }
    }
  }
}
