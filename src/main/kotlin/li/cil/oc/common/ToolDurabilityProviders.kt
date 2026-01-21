package li.cil.oc.common

import net.minecraft.item.ItemStack
import java.lang.reflect.Method

internal object ToolDurabilityProviders {
    private val providers = mutableListOf<Method>()

    @JvmStatic
    fun add(provider: Method) {
        providers.add(provider)
    }

    @JvmStatic
    fun getDurability(stack: ItemStack): Double? {
        for (provider in providers) {
            val durability = IMC.tryInvokeStatic(provider, stack, default = Double.NaN)
            if (!durability.isNaN()) return durability
        }
        // Fall back to vanilla damage values.
        return if (stack.isItemStackDamageable) {
            1.0 - stack.itemDamage.toDouble() / stack.maxDamage.toDouble()
        } else {
            null
        }
    }
}
