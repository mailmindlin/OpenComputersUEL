package li.cil.oc.common.component

import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.common.tileentity
import li.cil.oc.server.PacketSender as ServerPacketSender

class Screen(val screen: tileentity.Screen) : TextBuffer(screen) {
    @Callback(direct = true, doc = """function():boolean -- Whether touch mode is inverted (sneak-activate opens GUI, instead of normal activate).""")
    fun isTouchModeInverted(computer: Context, args: Arguments): Array<Any?> = result(screen.invertTouchMode)

    @Callback(doc = """function(value:boolean):boolean -- Sets whether to invert touch mode (sneak-activate opens GUI, instead of normal activate).""")
    fun setTouchModeInverted(computer: Context, args: Arguments): Array<Any?> {
        val newValue = args.checkBoolean(0)
        val oldValue = screen.invertTouchMode
        if (newValue != oldValue) {
            screen.invertTouchMode = newValue
            ServerPacketSender.sendScreenTouchMode(screen, newValue)
        }
        return result(oldValue)
    }
}
