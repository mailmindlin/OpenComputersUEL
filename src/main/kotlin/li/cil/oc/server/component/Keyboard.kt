package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Keyboard.UsabilityChecker
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Visibility
import net.minecraft.entity.player.EntityPlayer

// TODO key up when screen is disconnected from which the key down came
// TODO key up after load for anything that was pressed

class Keyboard(val host: EnvironmentHost) : ManagedEnvironmentKt(), li.cil.oc.api.internal.Keyboard, DeviceInfoKt {
    override val node = Network.newNode(this, Visibility.Network)
        .withComponent("keyboard")
        .create()

    val pressedKeys = mutableMapOf<EntityPlayer, MutableMap<Int, Char>>()

    var usableOverride: UsabilityChecker? = null

    override fun setUsableOverride(callback: UsabilityChecker?) {
        usableOverride = callback
    }

    // ----------------------------------------------------------------------- //

    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Input,
        DeviceAttribute.Description to "Keyboard",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "Fancytyper MX-Stone"
    )

    // ----------------------------------------------------------------------- //

    fun releasePressedKeys(player: EntityPlayer) {
        pressedKeys[player]?.let { keys ->
            for ((code, char) in keys) {
                if (Settings.get.inputUsername) {
                    signal(player, "key_up", char, code, player.name)
                } else {
                    signal(player, "key_up", char, code)
                }
            }
        }
        pressedKeys.remove(player)
    }

    // ----------------------------------------------------------------------- //

    override fun onMessage(message: Message) {
        when (message.name()) {
            "keyboard.keyDown" -> {
                val data = message.data()
                if (data.size == 3 && data[0] is EntityPlayer && data[1] is Char && data[2] is Int) {
                    val p = data[0] as EntityPlayer
                    val char = data[1] as Char
                    val code = data[2] as Int

                    if (isUsableByPlayer(p)) {
                        pressedKeys.getOrPut(p) { mutableMapOf() }[code] = char
                        if (Settings.get.inputUsername) {
                            signal(p, "key_down", char, code, p.name)
                        } else {
                            signal(p, "key_down", char, code)
                        }
                    }
                }
            }
            "keyboard.keyUp" -> {
                val data = message.data()
                if (data.size == 3 && data[0] is EntityPlayer && data[1] is Char && data[2] is Int) {
                    val p = data[0] as EntityPlayer
                    val char = data[1] as Char
                    val code = data[2] as Int

                    pressedKeys[p]?.let { keys ->
                        if (keys.containsKey(code)) {
                            keys.remove(code)
                            if (Settings.get.inputUsername) {
                                signal(p, "key_up", char, code, p.name)
                            } else {
                                signal(p, "key_up", char, code)
                            }
                        }
                    }
                }
            }
            "keyboard.clipboard" -> {
                val data = message.data()
                if (data.size == 2 && data[0] is EntityPlayer && data[1] is String) {
                    val p = data[0] as EntityPlayer
                    val value = data[1] as String

                    if (isUsableByPlayer(p)) {
                        for (line in value.lineSequence()) {
                            if (Settings.get.inputUsername) {
                                signal(p, "clipboard", line, p.name)
                            } else {
                                signal(p, "clipboard", line)
                            }
                        }
                    }
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    private fun isUsableByPlayer(p: EntityPlayer): Boolean {
        return usableOverride?.isUsableByPlayer(this, p)
            ?: (p.getDistanceSq(host.xPosition(), host.yPosition(), host.zPosition()) <= 64)
    }

    protected fun signal(vararg args: Any?) {
        node.sendToReachable("computer.checked_signal", *args)
    }
}
