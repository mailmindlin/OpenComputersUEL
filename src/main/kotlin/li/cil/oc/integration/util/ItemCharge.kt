package li.cil.oc.integration.util

import java.lang.reflect.Method

import li.cil.oc.common.IMC
import net.minecraft.item.ItemStack

internal object ItemCharge {
  private val chargers = LinkedHashSet<Pair<Method, Method>>()

  /**
   * @param canCharge: Method(ItemStack) -> Boolean
   * @param charge: Method(ItemStack, Double, Boolean) -> Double
   */
  fun add(canCharge: Method, charge: Method) {
    assert(canCharge.parameterTypes contentEquals arrayOf(ItemStack::class.java))
    assert(canCharge.returnType == Boolean::class.javaPrimitiveType)

    assert(charge.parameterTypes contentEquals arrayOf(ItemStack::class.java, Double::class.javaPrimitiveType, Boolean::class.javaPrimitiveType))
    assert(charge.returnType == Double::class.javaPrimitiveType)
    chargers += Pair(canCharge, charge)
  }

  fun canCharge(stack: ItemStack): Boolean = !stack.isEmpty && chargers.any { charger -> IMC.tryInvokeStatic(charger.first, stack, default = false) }

  /**
   * @return the amount of the delta that could not be applied.
   */
  fun charge(stack: ItemStack, amount: Double): Double {
    if (stack.isEmpty()) return amount
    val (_, charge) = chargers.firstOrNull { (canCharge, _) -> IMC.tryInvokeStatic(canCharge, stack, default = false) }
      ?: return amount
    return IMC.tryInvokeStatic(charge, stack, amount, false, default = amount)
  }
}
