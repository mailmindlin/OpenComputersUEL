package li.cil.oc.util

import net.minecraftforge.fml.common.FMLCommonHandler
import java.util.*

internal object SideTracker {
    private val serverThreads: MutableSet<Thread> = Collections.newSetFromMap(WeakHashMap())

    fun addServerThread() {
        serverThreads.add(Thread.currentThread())
    }

    fun isServer(): Boolean = FMLCommonHandler.instance().effectiveSide
        .isServer || serverThreads.contains(Thread.currentThread())
    fun isClient(): Boolean = !isServer()
}
