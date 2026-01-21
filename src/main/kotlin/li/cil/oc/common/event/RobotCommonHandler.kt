package li.cil.oc.common.event

import li.cil.oc.Settings
import li.cil.oc.api.event.RobotMoveEvent
import li.cil.oc.api.event.RobotUsedToolEvent
import li.cil.oc.api.internal.Robot
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.UpgradeHover
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.ExtendedWorld.extendedWorld
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object RobotCommonHandler {
    @JvmStatic
    @SubscribeEvent
    fun onRobotApplyDamageRate(e: RobotUsedToolEvent.ApplyDamageRate) {
        val agent = e.agent
        if (agent is Robot) {
            if (e.toolAfterUse.isItemStackDamageable) {
                val damage = e.toolAfterUse.itemDamage - e.toolBeforeUse.itemDamage
                if (damage > 0) {
                    val actualDamage = damage * e.damageRate
                    val repairedDamage = if (e.agent.player().rng.nextDouble() > 0.5) {
                        damage - Math.floor(actualDamage).toInt()
                    } else {
                        damage - Math.ceil(actualDamage).toInt()
                    }
                    e.toolAfterUse.itemDamage = e.toolAfterUse.itemDamage - repairedDamage
                }
            }
        }
    }

    @JvmStatic
    @SubscribeEvent
    fun onRobotMove(e: RobotMoveEvent.Pre) {
        if (Settings.get.limitFlightHeight >= 0) {
            val agent = e.agent
            if (agent is Robot) {
                val world = agent.world()
                var maxFlyingHeight = Settings.get.limitFlightHeight

                (0 until agent.equipmentInventory().sizeInventory)
                    .map { agent.equipmentInventory().getStackInSlot(it) }
                    .mapNotNull { Delegator.subItem(it) }
                    .filterIsInstance<UpgradeHover>()
                    .forEach { item -> maxFlyingHeight = maxOf(maxFlyingHeight, Settings.get.upgradeFlightHeight(item.tier)) }

                (0 until agent.componentCount())
                    .map { it + agent.mainInventory().sizeInventory + agent.equipmentInventory().sizeInventory }
                    .map { agent.getStackInSlot(it) }
                    .mapNotNull { Delegator.subItem(it) }
                    .filterIsInstance<UpgradeHover>()
                    .forEach { item -> maxFlyingHeight = maxOf(maxFlyingHeight, Settings.get.upgradeFlightHeight(item.tier)) }

                fun isMovingDown() = e.direction == EnumFacing.DOWN
                fun bypassesFlightLimit() = maxFlyingHeight >= world.height
                fun hasAdjacentBlock(pos: BlockPosition) = EnumFacing.values().any { side ->
                    world.extendedWorld().isSideSolid(pos.offset(side), side.opposite)
                }
                fun isWithinFlyingHeight(pos: BlockPosition) = (1..maxFlyingHeight).any { n ->
                    !world.isAirBlock(pos.offset(EnumFacing.DOWN, n).toBlockPos())
                }

                val startPos = BlockPosition(agent)
                val targetPos = startPos.offset(e.direction)
                // New movement rules as of 1.5:
                // 1. Robots may only move if the start or target position is valid (e.g. to allow building bridges).
                // 2. The position below a robot is always valid (can always move down).
                // 3. Positions up to <flightHeight> above a block are valid (limited flight capabilities).
                // 4. Any position that has an adjacent block with a solid face towards the position is valid (robots can "climb").
                val validMove = isMovingDown() ||
                    bypassesFlightLimit() ||
                    hasAdjacentBlock(startPos) ||
                    hasAdjacentBlock(targetPos) ||
                    isWithinFlyingHeight(startPos)

                if (!validMove) {
                    e.isCanceled = true
                }
            }
        }
    }
}
