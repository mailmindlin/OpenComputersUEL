package li.cil.oc.integration.computercraft

import dan200.computercraft.api.lua.ILuaContext
import dan200.computercraft.api.lua.LuaException
import dan200.computercraft.api.peripheral.IComputerAccess
import dan200.computercraft.api.peripheral.IPeripheral
import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Component
import li.cil.oc.common.tileentity.Relay
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.util.EnumFacing

class RelayPeripheral(val relay: Relay) : IPeripheral {
    private val methods = mapOf<String, (IComputerAccess, ILuaContext, Array<Any?>) -> Array<Any?>>(
        // Generic modem methods.
        "open" to { computer, context, arguments ->
            val port = checkPort(arguments, 0)
            if (relay.openPorts(computer).size >= 128)
                throw IllegalArgumentException("too many open channels")
            result(relay.openPorts(computer).add(port))
        },
        "isOpen" to { computer, context, arguments ->
            val port = checkPort(arguments, 0)
            result(relay.openPorts(computer).contains(port))
        },
        "close" to { computer, context, arguments ->
            val port = checkPort(arguments, 0)
            result(relay.openPorts(computer).remove(port))
        },
        "closeAll" to { computer, context, arguments ->
            relay.openPorts(computer).clear()
            emptyArray()
        },
        "transmit" to { computer, context, arguments ->
            val sendPort = checkPort(arguments, 0)
            val answerPort = checkPort(arguments, 1)
            val data = arguments.drop(2) + answerPort
            val packet = Network.newPacket(
                "cc${computer.id}_${computer.attachmentName}",
                null,
                sendPort,
                *data.toTypedArray()
            )
            result(relay.tryEnqueuePacket(null, packet))
        },
        "isWireless" to { computer, context, arguments ->
            // Let's pretend we're always wired, to allow accessing OC components
            // as remote peripherals when using an Access Point, too...
            result(false)
        },

        // Undocumented modem messages.
        "callRemote" to { computer, context, arguments ->
            val address = checkString(arguments, 0)
            val component = visibleComponents.find { it.address() == address }
            if (component != null) {
                val method = checkString(arguments, 1)
                val fakeContext = CCContext(computer, context)
                component.invoke(method, fakeContext, *arguments.drop(2).toTypedArray())
            } else {
                emptyArray()
            }
        },
        "getMethodsRemote" to { computer, context, arguments ->
            val address = checkString(arguments, 0)
            val component = visibleComponents.find { it.address() == address }
            if (component != null) {
                result(component.methods().withIndex().associate { (index, method) -> index + 1 to method })
            } else {
                emptyArray()
            }
        },
        "getNamesRemote" to { computer, context, arguments ->
            result(visibleComponents.map { it.address() }.withIndex().associate { (index, addr) -> index + 1 to addr })
        },
        "getTypeRemote" to { computer, context, arguments ->
            val address = checkString(arguments, 0)
            val component = visibleComponents.find { it.address() == address }
            if (component != null) {
                result(component.name())
            } else {
                emptyArray()
            }
        },
        "isPresentRemote" to { computer, context, arguments ->
            val address = checkString(arguments, 0)
            result(visibleComponents.any { it.address() == address })
        },

        // OC specific.
        "isAccessPoint" to { computer, context, arguments ->
            result(relay.isWirelessEnabled)
        },
        "isTunnel" to { computer, context, arguments ->
            result(relay.isLinkedEnabled)
        },
        "maxPacketSize" to { computer, context, arguments ->
            result(Settings.get.maxNetworkPacketSize)
        }
    )

    private val methodNames = methods.keys.sorted().toTypedArray()

    override fun getType() = "modem"

    override fun attach(computer: IComputerAccess) {
        relay.computers.add(computer)
        relay.openPorts[computer] = mutableSetOf()
    }

    override fun detach(computer: IComputerAccess) {
        relay.computers.remove(computer)
        relay.openPorts.remove(computer)
    }

    override fun getMethodNames(): Array<String> = methodNames

    override fun callMethod(computer: IComputerAccess, context: ILuaContext, method: Int, arguments: Array<Any?>): Array<Any?> =
        try {
            methods[methodNames[method]]?.invoke(computer, context, arguments) ?: emptyArray()
        } catch (e: LuaException) {
            throw e
        } catch (t: Throwable) {
            t.printStackTrace()
            throw LuaException(t.message)
        }

    override fun equals(other: IPeripheral): Boolean {
        return when (other) {
            is RelayPeripheral -> other.relay == relay
            else -> false
        }
    }

    private fun checkPort(args: Array<Any?>, index: Int): Int {
        if (args.size <= index || args[index] !is Number)
            throw IllegalArgumentException("bad argument #${index + 1} (number expected)")
        val port = (args[index] as Double).toInt()
        if (port < 0 || port > 0xFFFF)
            throw IllegalArgumentException("bad argument #${index + 1} (number in [1, 65535] expected)")
        return port
    }

    private fun checkString(args: Array<Any?>, index: Int): String {
        if (args.size <= index || args[index] !is String)
            throw IllegalArgumentException("bad argument #${index + 1} (string expected)")
        return args[index] as String
    }

    private val visibleComponents: Iterable<Component>
        get() = EnumFacing.values().flatMap { side ->
            val node = relay.sidedNode(side)
            node.reachableNodes().filterIsInstance<Component>().filter { it.canBeSeenFrom(node) }
        }

    inner class CCContext(val computer: IComputerAccess, val context: ILuaContext) : Context {
        override fun node() = relay.node()

        override fun isPaused() = false

        override fun stop() = false

        override fun canInteract(player: String) = true

        override fun signal(name: String, vararg args: Any?): Boolean {
            computer.queueEvent(name, args)
            return true
        }

        override fun pause(seconds: Double) = false

        override fun isRunning() = true

        override fun start() = false

        override fun consumeCallBudget(callCost: Double) {}
    }
}
