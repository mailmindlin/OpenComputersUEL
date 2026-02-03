package li.cil.oc.server.component.traits

import com.google.common.base.Charsets
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Packet
import li.cil.oc.util.Result
import li.cil.oc.util.result
import net.minecraft.nbt.NBTTagCompound

interface WakeMessageAware : NetworkAware {
    val wakeMessageHelper: WakeMessageHelper

    @Callback(direct = true, doc = """function():string, boolean -- Get the current wake-up message.""")
    fun getWakeMessage(context: Context, args: Arguments): Result {
        return result(wakeMessageHelper.wakeMessage, wakeMessageHelper.wakeMessageFuzzy)
    }

    @Callback(doc = """function(message:string[, fuzzy:boolean]):string -- Set the wake-up message and whether to ignore additional data/parameters.""")
    fun setWakeMessage(context: Context, args: Arguments): Result {
        val oldMessage = wakeMessageHelper.wakeMessage
        val oldFuzzy = wakeMessageHelper.wakeMessageFuzzy

        wakeMessageHelper.wakeMessage = if (args.optAny(0, null) == null) {
            null
        } else {
            args.checkString(0)
        }
        wakeMessageHelper.wakeMessageFuzzy = args.optBoolean(1, wakeMessageHelper.wakeMessageFuzzy)

        return result(oldMessage, oldFuzzy)
    }

    fun isPacketAccepted(packet: Packet, distance: Double): Boolean = true

    fun receivePacket(packet: Packet, distance: Double, host: EnvironmentHost?) {
        wakeMessageHelper.receivePacket(packet, distance, host, node!!, ::isPacketAccepted)
    }

    fun loadWakeMessage(nbt: NBTTagCompound) {
        wakeMessageHelper.load(nbt)
    }

    fun saveWakeMessage(nbt: NBTTagCompound) {
        wakeMessageHelper.save(nbt)
    }
}

class WakeMessageHelper {
    companion object {
        private const val WakeMessageTag = "wakeMessage"
        private const val WakeMessageFuzzyTag = "wakeMessageFuzzy"
    }

    var wakeMessage: String? = null
    var wakeMessageFuzzy: Boolean = false

    fun receivePacket(
        packet: Packet,
        distance: Double,
        host: EnvironmentHost?,
        node: li.cil.oc.api.network.Node,
        isPacketAccepted: (Packet, Double) -> Boolean
    ) {
        if (packet.source() != node.address() && (packet.destination() == null || packet.destination() == node.address())) {
            if (isPacketAccepted(packet, distance)) {
                node.sendToReachable(
                    "computer.signal",
                    "modem_message",
                    packet.source(),
                    packet.port(),
                    distance,
                    *packet.data()
                )
            }

            // Accept wake-up messages regardless of port because we close all ports
            // when our computer shuts down.
            val wakeup = when {
                wakeMessage == null -> false
                else -> {
                    val data = packet.data()
                    when {
                        data.size == 1 && data[0] is ByteArray -> {
                            val message = String(data[0] as ByteArray, Charsets.UTF_8)
                            wakeMessage == message
                        }
                        data.size == 1 && data[0] is String -> {
                            wakeMessage == data[0]
                        }
                        wakeMessageFuzzy && data.isNotEmpty() && data[0] is ByteArray -> {
                            val message = String(data[0] as ByteArray, Charsets.UTF_8)
                            wakeMessage == message
                        }
                        wakeMessageFuzzy && data.isNotEmpty() && data[0] is String -> {
                            wakeMessage == data[0]
                        }
                        else -> false
                    }
                }
            }

            if (wakeup) {
                when (host) {
                    is Context -> host.start()
                    else -> node.sendToNeighbors("computer.start")
                }
            }
        }
    }

    fun load(nbt: NBTTagCompound) {
        if (nbt.hasKey(WakeMessageTag)) {
            wakeMessage = nbt.getString(WakeMessageTag)
        }
        wakeMessageFuzzy = nbt.getBoolean(WakeMessageFuzzyTag)
    }

    fun save(nbt: NBTTagCompound) {
        wakeMessage?.let { nbt.setString(WakeMessageTag, it) }
        nbt.setBoolean(WakeMessageFuzzyTag, wakeMessageFuzzy)
    }
}
