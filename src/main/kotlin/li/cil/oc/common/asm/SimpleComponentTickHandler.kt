package li.cil.oc.common.asm

import li.cil.oc.api.Network
import li.cil.oc.util.SideTracker
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

// This class is used for adding simple components to the component network.
// It is triggered from a validate call, and executed in the next update tick.
class SimpleComponentTickHandler private constructor() {
    @SubscribeEvent
    fun onTick(e: ServerTickEvent) {
        if (e.phase == TickEvent.Phase.START) {
            val adds: Array<Runnable>
            synchronized(pending) {
                adds = pending.toTypedArray<Runnable>()
                pending.clear()
            }
            for (runnable in adds) {
                try {
                    runnable.run()
                } catch (t: Throwable) {
                    log.warn("Error in scheduled tick action.", t)
                }
            }
        }
    }

    companion object {
        private val log: Logger = LogManager.getLogger("OpenComputers")

        val pending: ArrayList<Runnable> = ArrayList()

        val Instance: SimpleComponentTickHandler = SimpleComponentTickHandler()

        @JvmStatic
        fun schedule(tileEntity: TileEntity) {
            if (SideTracker.isServer()) {
                synchronized(pending) {
                    pending.add(
                        Runnable { Network.joinOrCreateNetwork(tileEntity) })
                }
            }
        }
    }
}
