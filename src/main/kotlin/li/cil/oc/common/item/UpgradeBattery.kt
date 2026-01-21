package li.cil.oc.common.item

import li.cil.oc.Settings
import li.cil.oc.common.item.data.NodeData
import li.cil.oc.common.item.traits.Chargeable
import li.cil.oc.common.item.traits.Delegate
import li.cil.oc.common.item.traits.ItemTier
import net.minecraft.item.ItemStack

class UpgradeBattery(override val parent: Delegator, val tier: Int) : Delegate, ItemTier, Chargeable {
    override val unlocalizedName: String = super.unlocalizedName + tier

    override val tooltipName: String? get() = super.unlocalizedName

    override val tooltipData: List<Any> get() = listOf(Settings.get.bufferCapacitorUpgrades(tier).toInt())

    override fun showDurabilityBar(stack: ItemStack): Boolean = true

    override fun durability(stack: ItemStack): Double {
        val data = NodeData(stack)
        return 1 - (data.buffer ?: 0.0) / Settings.get.bufferCapacitorUpgrades(tier)
    }

    // ----------------------------------------------------------------------- //

    override fun canCharge(stack: ItemStack): Boolean = true

    override fun charge(stack: ItemStack, amount: Double, simulate: Boolean): Double {
        val data = NodeData(stack)
        val buffer = data.buffer ?: 0.0
        return Chargeable.applyCharge(amount, buffer, Settings.get.bufferCapacitorUpgrades(tier)) { used ->
            if (!simulate) {
                data.buffer = buffer + used
                data.save(stack)
            }
        }
    }

    override fun maxCharge(stack: ItemStack): Double = Settings.get.bufferCapacitorUpgrades(tier)

    override fun getCharge(stack: ItemStack): Double = NodeData(stack).buffer ?: 0.0

    override fun setCharge(stack: ItemStack, amount: Double) {
        val data = NodeData(stack)
        data.buffer = (0.0.coerceAtLeast(amount)).coerceAtMost(maxCharge(stack))
        data.save(stack)
    }

    override fun canExtract(stack: ItemStack): Boolean = true
}
