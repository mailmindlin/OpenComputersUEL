package li.cil.oc.server.agent

import li.cil.oc.OpenComputers
import li.cil.oc.api.network.Node
import li.cil.oc.util.OCObfuscationReflectionHelper
import net.minecraft.server.management.PlayerInteractionManager
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.world.BlockEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

object PlayerInteractionManagerHelper {

    private fun isDestroyingBlock(player: Player): Boolean {
        return try {
            OCObfuscationReflectionHelper.getPrivateValue(
                PlayerInteractionManager::class.java,
                player.interactionManager,
                "field_73088_d"
            ) as Boolean
        } catch (_: Exception) {
            true
        }
    }

    fun onBlockClicked(player: Player, pos: BlockPos, side: EnumFacing): Boolean {
        if (isDestroyingBlock(player)) {
            player.interactionManager.cancelDestroyingBlock()
        }
        player.interactionManager.onBlockClicked(pos, side)
        return isDestroyingBlock(player)
    }

    fun updateBlockRemoving(player: Player): Boolean {
        if (!isDestroyingBlock(player))
            return false
        player.interactionManager.updateBlockRemoving()
        return isDestroyingBlock(player)
    }

    // returns exp gained from removing the block, -1 if block not removed
    // redone here because the interaction manager just drops the xp on the ground
    fun blockRemoving(player: Player, pos: BlockPos): Int {
        if (!isDestroyingBlock(player)) {
            return -1
        }

        val infBreaker = object {
            var expToDrop: Int = 0

            val hasExperienceUpgrade: Boolean = run {
                val machineNode = player.agent.machine().node()
                machineNode.reachableNodes().any { node ->
                    if (node is Node && node.canBeReachedFrom(machineNode)) {
                        node.host() is li.cil.oc.common.item.UpgradeExperience ||
                            node.host() is li.cil.oc.server.component.UpgradeExperience
                    } else {
                        false
                    }
                }
            }

            @SubscribeEvent(priority = EventPriority.LOWEST)
            fun onBreakSpeedEvent(breakSpeedEvent: PlayerEvent.BreakSpeed) {
                if (player == breakSpeedEvent.entityPlayer)
                    breakSpeedEvent.newSpeed = Float.MAX_VALUE
            }

            @SubscribeEvent(priority = EventPriority.LOWEST)
            fun onExperienceBreakEvent(experienceBreakEvent: BlockEvent.BreakEvent) {
                if (player == experienceBreakEvent.player) {
                    if (hasExperienceUpgrade) {
                        expToDrop += experienceBreakEvent.expToDrop
                        experienceBreakEvent.expToDrop = 0
                    }
                }
            }
        }

        MinecraftForge.EVENT_BUS.register(infBreaker)
        return try {
            player.interactionManager.blockRemoving(pos)
            infBreaker.expToDrop
        } catch (e: Exception) {
            OpenComputers.log.info("an exception was thrown while trying to call blockRemoving: ${e.message}")
            player.interactionManager.cancelDestroyingBlock()
            -1
        } finally {
            MinecraftForge.EVENT_BUS.unregister(infBreaker)
        }
    }
}
