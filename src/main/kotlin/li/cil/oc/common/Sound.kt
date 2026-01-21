package li.cil.oc.common

import li.cil.oc.Settings
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.server.PacketSender
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import java.util.WeakHashMap

object Sound {
    private val globalTimeouts = WeakHashMap<EnvironmentHost, MutableMap<String, Long>>()

    @JvmStatic
    @Synchronized
    fun play(host: EnvironmentHost, name: String) {
        val hostTimeouts = globalTimeouts[host]
        if (hostTimeouts != null && (hostTimeouts[name] ?: 0L) > System.currentTimeMillis()) {
            // Cooldown.
            return
        }
        PacketSender.sendSound(
            host.world(),
            host.xPosition(),
            host.yPosition(),
            host.zPosition(),
            ResourceLocation(Settings.resourceDomain + ":" + name),
            SoundCategory.BLOCKS,
            (15 * Settings.get.soundVolume).toDouble()
        )
        globalTimeouts.getOrPut(host) { mutableMapOf() }[name] = System.currentTimeMillis() + 500
    }

    @JvmStatic
    fun playDiskInsert(host: EnvironmentHost) {
        play(host, "floppy_insert")
    }

    @JvmStatic
    fun playDiskEject(host: EnvironmentHost) {
        play(host, "floppy_eject")
    }
}
