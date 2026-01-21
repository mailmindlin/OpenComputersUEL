package li.cil.oc.common.nanomachines

import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.nanomachines.BehaviorProvider
import li.cil.oc.api.nanomachines.Controller
import li.cil.oc.server.PacketSender
import li.cil.oc.util.PlayerUtils
import net.minecraft.entity.player.EntityPlayer
import java.util.WeakHashMap

object Nanomachines : api.detail.NanomachinesAPI {
    @JvmField
    val providers: MutableSet<BehaviorProvider> = mutableSetOf()

    @JvmField
    val serverControllers: WeakHashMap<EntityPlayer, ControllerImpl> = WeakHashMap()

    @JvmField
    val clientControllers: WeakHashMap<EntityPlayer, ControllerImpl> = WeakHashMap()

    @JvmStatic
    fun controllers(player: EntityPlayer): WeakHashMap<EntityPlayer, ControllerImpl> =
        if (player.entityWorld.isRemote) clientControllers else serverControllers

    override fun addProvider(provider: BehaviorProvider) {
        providers.add(provider)
    }

    override fun getProviders(): Iterable<BehaviorProvider> = providers

    override fun getController(player: EntityPlayer): Controller? {
        return if (hasController(player)) {
            controllers(player).getOrPut(player) { ControllerImpl(player) }
        } else {
            null
        }
    }

    @JvmStatic
    fun hasController(player: EntityPlayer): Boolean {
        return PlayerUtils.persistedData(player).getBoolean(Settings.namespace + "hasNanomachines")
    }

    @JvmStatic
    fun installController(player: EntityPlayer): Controller? {
        if (!hasController(player)) {
            PlayerUtils.persistedData(player).setBoolean(Settings.namespace + "hasNanomachines", true)
        }
        return getController(player) // Initialize controller instance.
    }

    override fun uninstallController(player: EntityPlayer) {
        val controller = getController(player)
        if (controller is ControllerImpl) {
            controller.dispose()
            controllers(player).remove(player)
            PlayerUtils.persistedData(player).removeTag(Settings.namespace + "hasNanomachines")
            if (!player.entityWorld.isRemote) {
                PacketSender.sendNanomachineConfiguration(player)
            }
        }
    }
}
