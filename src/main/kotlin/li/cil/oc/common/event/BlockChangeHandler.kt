package li.cil.oc.common.event

import li.cil.oc.common.EventHandler
import li.cil.oc.util.BlockPosition
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.SoundCategory
import net.minecraft.util.SoundEvent
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IWorldEventListener
import net.minecraft.world.World
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.util.WeakHashMap

/**
 * @author Vexatos
 */
object BlockChangeHandler {

    @JvmStatic
    fun addListener(listener: ChangeListener, coord: BlockPosition) {
        EventHandler.scheduleServer { changeListeners[listener] = coord }
    }

    @JvmStatic
    fun removeListener(listener: ChangeListener) {
        EventHandler.scheduleServer { changeListeners.remove(listener) }
    }

    private val changeListeners = WeakHashMap<ChangeListener, BlockPosition>()

    @JvmStatic
    @SubscribeEvent
    fun onWorldLoad(e: WorldEvent.Load) {
        e.world.addEventListener(Listener(e.world))
    }

    interface ChangeListener {
        fun onBlockChanged()
    }

    private class Listener(private val world: World) : IWorldEventListener {
        override fun notifyBlockUpdate(worldIn: World, pos: BlockPos, oldState: IBlockState, newState: IBlockState, flags: Int) {
            val current = BlockPosition(pos, world)
            for ((listener, coord) in changeListeners) {
                if (coord == current) {
                    listener.onBlockChanged()
                }
            }
        }

        override fun spawnParticle(id: Int, ignoreRange: Boolean, p_190570_3_: Boolean, x: Double, y: Double, z: Double, xSpeed: Double, ySpeed: Double, zSpeed: Double, vararg parameters: Int) {}

        override fun playRecord(soundIn: SoundEvent, pos: BlockPos) {}

        override fun playEvent(player: EntityPlayer?, type: Int, blockPosIn: BlockPos, data: Int) {}

        override fun onEntityAdded(entityIn: Entity) {}

        override fun spawnParticle(particleID: Int, ignoreRange: Boolean, xCoord: Double, yCoord: Double, zCoord: Double, xOffset: Double, yOffset: Double, zOffset: Double, vararg parameters: Int) {}

        override fun onEntityRemoved(entityIn: Entity) {}

        override fun broadcastSound(soundID: Int, pos: BlockPos, data: Int) {}

        override fun playSoundToAllNearExcept(player: EntityPlayer?, soundIn: SoundEvent, category: SoundCategory, x: Double, y: Double, z: Double, volume: Float, pitch: Float) {}

        override fun markBlockRangeForRenderUpdate(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) {}

        override fun sendBlockBreakProgress(breakerId: Int, pos: BlockPos, progress: Int) {}

        override fun notifyLightSet(pos: BlockPos) {}
    }
}
