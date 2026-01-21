package li.cil.oc.integration.ic2

import ic2.api.item.ElectricItem
import ic2.api.item.IElectricItem
import ic2.api.item.ISpecialElectricItem
import ic2.core.item.tool.ItemToolWrench
import li.cil.oc.api.event.RobotUsedToolEvent
import li.cil.oc.integration.util.Power
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.math.ceil
import kotlin.math.floor

object EventHandlerIndustrialCraft2 {
    @SubscribeEvent
    fun onRobotApplyDamageRate(e: RobotUsedToolEvent.ApplyDamageRate) {
        val optManagerBefore = when (val item = e.toolBeforeUse.item) {
            is ISpecialElectricItem -> item.getManager(e.toolBeforeUse)
            is IElectricItem -> ElectricItem.manager
            else -> null
        }
        val optManagerAfter = when (val item = e.toolAfterUse.item) {
            is ISpecialElectricItem -> item.getManager(e.toolAfterUse)
            is IElectricItem -> ElectricItem.manager
            else -> null
        }
        if (optManagerBefore != null && optManagerAfter != null) {
            val damage = optManagerBefore.getCharge(e.toolBeforeUse) - optManagerAfter.getCharge(e.toolAfterUse)
            if (damage > 0) {
                val actualDamage = damage * e.damageRate
                val repairedDamage = if (e.agent.player().rng.nextDouble() > 0.5) {
                    damage - floor(actualDamage).toInt()
                } else {
                    damage - ceil(actualDamage).toInt()
                }
                optManagerAfter.charge(e.toolAfterUse, repairedDamage, Int.MAX_VALUE, true, false)
            }
        }
    }

    @JvmStatic
    fun getDurability(stack: ItemStack): Double {
        return when (val item = stack.item) {
            is ISpecialElectricItem -> {
                val manager = item.getManager(stack)
                manager.getCharge(stack) / manager.getMaxCharge(stack)
            }
            is IElectricItem -> {
                ElectricItem.manager.getCharge(stack) / item.getMaxCharge(stack)
            }
            else -> Double.NaN
        }
    }

    @JvmStatic
    fun useWrench(player: EntityPlayer, pos: BlockPos, changeDurability: Boolean): Boolean {
        return when (val item = player.heldItemMainhand.item) {
            is ItemToolWrench -> {
                if (changeDurability) {
                    item.damage(player.heldItemMainhand, 1, player)
                    true
                } else {
                    item.canTakeDamage(player.heldItemMainhand, 1)
                }
            }
            else -> false
        }
    }

    @JvmStatic
    fun isWrench(stack: ItemStack): Boolean = stack.item is ItemToolWrench

    @JvmStatic
    fun canCharge(stack: ItemStack): Boolean {
        return when (val item = stack.item) {
            is IElectricItem -> item.getMaxCharge(stack) > 0
            else -> false
        }
    }

    @JvmStatic
    fun charge(stack: ItemStack, amount: Double, simulate: Boolean): Double {
        val manager = when (val item = stack.item) {
            is ISpecialElectricItem -> item.getManager(stack)
            is IElectricItem -> ElectricItem.manager
            else -> null
        }
        return if (manager != null) {
            amount - Power.fromEU(manager.charge(stack, Power.toEU(amount), Int.MAX_VALUE, true, false))
        } else {
            amount
        }
    }
}
