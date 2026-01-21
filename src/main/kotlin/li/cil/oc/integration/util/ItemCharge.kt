package li.cil.oc.integration.util

import java.lang.reflect.Method

import li.cil.oc.common.IMC
import net.minecraft.item.ItemStack

internal object ItemCharge {
  private val chargers = LinkedHashSet<Pair<Method, Method>>()

  fun add(canCharge: Method, charge: Method) {
    chargers += Pair(canCharge, charge)
  }

  fun canCharge(stack: ItemStack): Boolean = !stack.isEmpty && chargers.any { charger -> IMC.tryInvokeStatic(charger.first, stack)(false) }

  /**
   * @return the amount of the delta that could not be applied.
   */
  fun charge(stack: ItemStack, amount: Double): Double = {
    if (!stack.isEmpty) chargers.find(charger => IMC.tryInvokeStatic(charger._1, stack)(false)) match {
      case Some(charger) => IMC.tryInvokeStatic(charger._2, stack, Double.box(amount), java.lang.Boolean.FALSE)(amount)
      case _ => amount
    }
    else amount
  }
}
