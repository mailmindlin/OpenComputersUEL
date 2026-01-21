package li.cil.oc.integration.util

import li.cil.oc.server.component.RedstoneWireless

object WirelessRedstone {
    val systems = mutableSetOf<WirelessRedstoneSystem>()

    fun isAvailable(): Boolean = systems.isNotEmpty()

    fun addReceiver(rs: RedstoneWireless) {
        systems.forEach { system ->
            try {
                system.addReceiver(rs)
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }

    fun removeReceiver(rs: RedstoneWireless) {
        systems.forEach { system ->
            try {
                system.removeReceiver(rs)
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }

    fun updateOutput(rs: RedstoneWireless) {
        systems.forEach { system ->
            try {
                system.updateOutput(rs)
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }

    fun removeTransmitter(rs: RedstoneWireless) {
        systems.forEach { system ->
            try {
                system.removeTransmitter(rs)
            } catch (_: Throwable) {
                // Ignore
            }
        }
    }

    fun getInput(rs: RedstoneWireless): Boolean = systems.any { it.getInput(rs) }

    interface WirelessRedstoneSystem {
        fun addReceiver(rs: RedstoneWireless)

        fun removeReceiver(rs: RedstoneWireless)

        fun updateOutput(rs: RedstoneWireless)

        fun removeTransmitter(rs: RedstoneWireless)

        fun getInput(rs: RedstoneWireless): Boolean
    }
}
