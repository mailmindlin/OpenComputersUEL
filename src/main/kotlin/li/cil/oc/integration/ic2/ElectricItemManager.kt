package li.cil.oc.integration.ic2

import ic2.api.item.IElectricItem
import ic2.api.item.IElectricItemManager
import li.cil.oc.Settings
import li.cil.oc.api.driver.item.Chargeable
import li.cil.oc.integration.util.Power
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack

object ElectricItemManager : IElectricItemManager {
    override fun getCharge(stack: ItemStack?): Double {
        if (stack == null) return 0.0
        return when (val item = stack.item) {
            is Chargeable -> Power.toEU(Int.MAX_VALUE + item.charge(stack, -Int.MAX_VALUE, true))
            else -> 0.0
        }
    }

    override fun charge(stack: ItemStack?, amount: Double, tier: Int, ignoreTransferLimit: Boolean, simulate: Boolean): Double {
        if (stack == null) return 0.0
        return when (val item = stack.item) {
            is Chargeable -> {
                val limitedAmount = if (ignoreTransferLimit) {
                    minOf(Int.MAX_VALUE.toDouble(), amount)
                } else {
                    minOf(amount, Settings.get.chargeRateTablet)
                }
                limitedAmount - Power.toEU(item.charge(stack, Power.fromEU(limitedAmount), simulate))
            }
            else -> 0.0
        }
    }

    override fun discharge(stack: ItemStack?, amount: Double, tier: Int, ignoreTransferLimit: Boolean, externally: Boolean, simulate: Boolean): Double {
        return 0.0 // TODO if we ever need it...
    }

    override fun chargeFromArmor(stack: ItemStack?, entity: EntityLivingBase?) {}

    override fun canUse(stack: ItemStack?, amount: Double): Boolean = getCharge(stack) >= amount

    override fun use(stack: ItemStack?, amount: Double, entity: EntityLivingBase?): Boolean = canUse(stack, amount) && false // TODO if we ever need it...

    override fun getToolTip(stack: ItemStack?): String = ""

    override fun getMaxCharge(stack: ItemStack?): Double {
        if (stack == null) return 0.0
        return when (val item = stack.item) {
            is IElectricItem -> item.getMaxCharge(stack)
            else -> 0.0
        }
    }

    override fun getTier(stack: ItemStack?): Int {
        if (stack == null) return 0
        return when (val item = stack.item) {
            is IElectricItem -> item.getTier(stack)
            else -> 0
        }
    }
}
