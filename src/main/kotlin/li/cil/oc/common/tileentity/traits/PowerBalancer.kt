package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.SidedEnvironment
import li.cil.oc.util.mapArray
import net.minecraft.util.EnumFacing

interface PowerBalancer : PowerInformation, SidedEnvironment, Tickable {
    val isConnected: Boolean
    override val powerDelegate: Delegate

    class Delegate(tile: PowerBalancer) : PowerInformation.Delegate(tile) {
        protected open fun distribute(): Pair<Double, Double> {
            var sumBuffer = 0.0
            var sumSize = 0.0
            for (node in connectors) {
                if (node != null && isPrimary(node)) {
                    sumBuffer += node.globalBuffer()
                    sumSize += node.globalBufferSize()
                }
            }
            return Pair(sumBuffer, sumSize)
        }

        private val connectors: Array<Connector?> get() {
            val tile = tile as PowerBalancer
            return EnumFacing.values().mapArray { side -> tile.sidedNode(side) as? Connector }
        }

        private fun isPrimary(connector: Connector): Boolean {
            val nodes = connectors
            val index = nodes.indexOfFirst { it != null && it.network() == connector.network() }
            return index >= 0 && nodes[index] == connector
        }

        internal fun update() {
            val nodes = connectors
            fun network(connector: Connector?) = connector?.network() ?: this
            // Yeeeeah, so that just happened... it's not a beauty, but it works. This
            // is necessary because power in networks can be updated asynchronously,
            // i.e. in separate threads (e.g. to allow screens to consume energy when
            // they change, which usually happens in a computers executor thread).
            // This multi-lock only happens in the main server thread, though, so we
            // don't have to fear deadlocks. I think.
            synchronized(network(nodes[0])) {
                synchronized(network(nodes[1])) {
                    synchronized(network(nodes[2])) {
                        synchronized(network(nodes[3])) {
                            synchronized(network(nodes[4])) {
                                synchronized(network(nodes[5])) {
                                    val (sumBuffer, sumSize) = distribute()
                                    if (sumSize > 0) {
                                        val ratio = sumBuffer / sumSize
                                        for (node in connectors) {
                                            if (node != null && isPrimary(node)) {
                                                node.changeBuffer(node.globalBufferSize() * ratio - node.globalBuffer())
                                            }
                                        }
                                    }
                                    this.tile.globalBuffer = sumBuffer
                                    this.tile.globalBufferSize = sumSize
                                }
                            }
                        }
                    }
                }
            }
            this.updatePowerInformation()
        }
    }

    override fun updateEntity() {
//        super.updateEntity()
        if (isServer && isConnected && Settings.get.isTickMultiple(world!!)) {
            this.powerDelegate.update()
        }
    }
}
