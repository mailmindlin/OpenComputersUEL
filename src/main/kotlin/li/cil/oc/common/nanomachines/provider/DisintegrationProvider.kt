package li.cil.oc.common.nanomachines.provider

import li.cil.oc.Settings
import li.cil.oc.api.Nanomachines as ApiNanomachines
import li.cil.oc.api.nanomachines.Behavior
import li.cil.oc.api.nanomachines.DisableReason
import li.cil.oc.api.prefab.AbstractBehavior
import li.cil.oc.util.BlockPosition
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.world.World
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.event.entity.player.PlayerInteractEvent
import net.minecraftforge.fml.common.eventhandler.Event

object DisintegrationProvider : ScalaProvider("c4e7e3c2-8069-4fbb-b08e-74b1bddcdfe7") {
    override fun createScalaBehaviors(player: EntityPlayer): Iterable<Behavior> = listOf(DisintegrationBehavior(player))

    override fun readBehaviorFromNBT(player: EntityPlayer, nbt: NBTTagCompound): Behavior = DisintegrationBehavior(player)

    class DisintegrationBehavior(p: EntityPlayer) : AbstractBehavior(p) {
        var breakingMap: MutableMap<BlockPosition, SlowBreakInfo> = mutableMapOf()
        var breakingMapNew: MutableMap<BlockPosition, SlowBreakInfo> = mutableMapOf()

        // Note: intentionally not overriding getNameHint. Gotta find this one manually!

        override fun onDisable(reason: DisableReason) {
            val world = player.entityWorld
            for (pos in breakingMap.keys) {
                world.sendBlockBreakProgress(pos.hashCode(), pos.toBlockPos(), -1)
            }
            breakingMap.clear()
        }

        override fun update() {
            val world = player.entityWorld
            if (!world.isRemote) {
                when (player) {
                    is FakePlayer -> return // Nope
                    is EntityPlayerMP -> {
                        val playerMP = player
                        val now = world.totalWorldTime

                        // Check blocks in range.
                        val blockPos = BlockPosition(player)
                        val actualRange = Settings.get.nanomachineDisintegrationRange * ApiNanomachines.getController(player).getInputCount(this)
                        for (x in -actualRange..actualRange) {
                            for (y in 0..(actualRange * 2)) {
                                for (z in -actualRange..actualRange) {
                                    val pos = BlockPosition(blockPos.offset(x.toDouble(), y.toDouble(), z.toDouble()))
                                    val existingInfo = breakingMap[pos]
                                    when {
                                        existingInfo != null && existingInfo.checkTool(player) -> {
                                            breakingMapNew[pos] = existingInfo
                                            existingInfo.update(world, player, now)
                                        }
                                        existingInfo == null -> {
                                            val event = PlayerInteractEvent.LeftClickBlock(player, pos.toBlockPos(), player.horizontalFacing, null)
                                            MinecraftForge.EVENT_BUS.post(event)
                                            val allowed = !event.isCanceled && event.useBlock != Event.Result.DENY && event.useItem != Event.Result.DENY
                                            val adventureOk = !world.worldInfo.gameType.hasLimitedInteractions() || player.canPlayerEdit(pos.toBlockPos(), null, player.heldItemMainhand)
                                            if (allowed && adventureOk && !world.isAirBlock(pos.toBlockPos())) {
                                                val blockState = world.getBlockState(pos.toBlockPos())
                                                val hardness = blockState.getPlayerRelativeBlockHardness(player, world, pos.toBlockPos())
                                                if (hardness > 0) {
                                                    val timeToBreak = (1 / hardness).toInt()
                                                    if (timeToBreak < 20 * 30) {
                                                        val stackOption = if (player.heldItemMainhand.isEmpty) null else player.heldItemMainhand.copy()
                                                        val info = SlowBreakInfo(now, now + timeToBreak, pos, stackOption, blockState)
                                                        world.sendBlockBreakProgress(pos.hashCode(), pos.toBlockPos(), 0)
                                                        breakingMapNew[pos] = info
                                                    }
                                                }
                                            }
                                        }
                                        // else: Tool changed, pretend block doesn't exist for this tick.
                                    }
                                }
                            }
                        }

                        // Handle completed breaks.
                        for ((pos, info) in breakingMap) {
                            if (info.timeBroken < now) {
                                breakingMapNew.remove(pos)
                                info.finish(world, playerMP)
                            }
                        }

                        // Handle aborted / incomplete breaks.
                        for (pos in breakingMap.keys - breakingMapNew.keys) {
                            world.sendBlockBreakProgress(pos.hashCode(), pos.toBlockPos(), -1)
                        }

                        val tmp = breakingMap
                        breakingMap.clear()
                        breakingMap = breakingMapNew
                        breakingMapNew = tmp
                    }
                    // else: Not available for fake players, sorry :P
                }
            }
        }
    }

    class SlowBreakInfo(
        val timeStarted: Long,
        val timeBroken: Long,
        val pos: BlockPosition,
        val originalTool: ItemStack?,
        val blockState: IBlockState
    ) {
        var lastDamageSent: Int = 0

        fun checkTool(player: EntityPlayer): Boolean {
            val currentTool = if (player.heldItemMainhand.isEmpty) null else player.heldItemMainhand.copy()
            return when {
                currentTool != null && originalTool != null -> {
                    currentTool.item == originalTool.item && (currentTool.isItemStackDamageable || currentTool.itemDamage == originalTool.itemDamage)
                }
                currentTool == null && originalTool == null -> true
                else -> false
            }
        }

        fun update(world: World, player: EntityPlayer, now: Long) {
            val timeTotal = timeBroken - timeStarted
            if (timeTotal > 0) {
                val timeTaken = now - timeStarted
                val damage = 10 * timeTaken / timeTotal
                if (damage.toInt() != lastDamageSent) {
                    lastDamageSent = damage.toInt()
                    world.sendBlockBreakProgress(pos.hashCode(), pos.toBlockPos(), lastDamageSent)
                }
            }
        }

        fun finish(world: World, player: EntityPlayerMP) {
            val sameBlock = world.getBlockState(pos.toBlockPos()) == blockState
            if (sameBlock) {
                world.sendBlockBreakProgress(pos.hashCode(), pos.toBlockPos(), -1)
                if (player.interactionManager.tryHarvestBlock(pos.toBlockPos())) {
                    world.playEvent(2001, pos.toBlockPos(), Block.getIdFromBlock(blockState.block) + (blockState.block.getMetaFromState(blockState) shl 12))
                }
            }
        }
    }
}
