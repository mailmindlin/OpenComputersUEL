package li.cil.oc.common.event

import li.cil.oc.Settings
import li.cil.oc.common.item.HoverBoots
import net.minecraft.entity.player.EntityPlayer
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent
import net.minecraftforge.event.entity.living.LivingFallEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object HoverBootsHandler {
    @JvmStatic
    @SubscribeEvent
    fun onLivingUpdate(e: LivingUpdateEvent) {
        val entity = e.entity
        if (entity is EntityPlayer && entity !is FakePlayer) {
            val player = entity
            val nbt = player.entityData
            val hadHoverBoots = nbt.getBoolean(Settings.namespace + "hasHoverBoots")
            val hasHoverBoots = !player.isSneaking && equippedArmor(player).any { stack ->
                val item = stack.item
                if (item is HoverBoots) {
                    Settings.get.ignorePower || run {
                        if (player.onGround && !player.capabilities.isCreativeMode && player.world.totalWorldTime % Settings.get.tickFrequency == 0L) {
                            val velocity = player.motionX * player.motionX + player.motionY * player.motionY + player.motionZ * player.motionZ
                            if (velocity > 0.015f) {
                                item.charge(stack, -Settings.get.hoverBootMove, false)
                            }
                        }
                        item.getCharge(stack) > 0
                    }
                } else false
            }
            if (hasHoverBoots != hadHoverBoots) {
                nbt.setBoolean(Settings.namespace + "hasHoverBoots", hasHoverBoots)
                player.stepHeight = if (hasHoverBoots) 1f else 0.5f
            }
            if (hasHoverBoots && !player.onGround && player.fallDistance < 5 && player.motionY < 0) {
                player.motionY *= 0.9f
            }
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onLivingJump(e: LivingJumpEvent) {
        val entity = e.entity
        if (entity is EntityPlayer && entity !is FakePlayer && !entity.isSneaking) {
            equippedArmor(entity).firstOrNull { it.item is HoverBoots }?.let { stack ->
                val boots = stack.item as HoverBoots
                val hoverJumpCost = -Settings.get.hoverBootJump
                val isCreative = Settings.get.ignorePower || entity.capabilities.isCreativeMode
                if (isCreative || boots.charge(stack, hoverJumpCost, true) == 0.0) {
                    if (!isCreative) boots.charge(stack, hoverJumpCost, false)
                    if (entity.isSprinting)
                        entity.addVelocity(entity.motionX * 0.5, 0.4, entity.motionZ * 0.5)
                    else
                        entity.addVelocity(0.0, 0.4, 0.0)
                }
            }
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onLivingFall(e: LivingFallEvent) {
        if (e.distance > 3) {
            val entity = e.entity
            if (entity is EntityPlayer && entity !is FakePlayer) {
                equippedArmor(entity).firstOrNull { it.item is HoverBoots }?.let { stack ->
                    val boots = stack.item as HoverBoots
                    val hoverFallCost = -Settings.get.hoverBootAbsorb
                    val isCreative = Settings.get.ignorePower || entity.capabilities.isCreativeMode
                    if (isCreative || boots.charge(stack, hoverFallCost, true) == 0.0) {
                        if (!isCreative) boots.charge(stack, hoverFallCost, false)
                        e.distance = e.distance * 0.3f
                    }
                }
            }
        }
    }

    private fun equippedArmor(player: EntityPlayer) = player.armorInventoryList.filter { !it.isEmpty }
}
