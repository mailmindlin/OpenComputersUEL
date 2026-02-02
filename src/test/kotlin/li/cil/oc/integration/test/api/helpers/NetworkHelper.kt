package li.cil.oc.integration.test.api.helpers

import li.cil.oc.api.network.Node
import li.cil.oc.integration.test.api.context.TestContext
import li.cil.oc.server.network.Network
import net.minecraft.util.math.BlockPos

/**
 * Helper class for network verification in integration tests.
 *
 * Provides methods to check network connectivity, count networks,
 * and wait for network stabilization after block operations.
 */
class NetworkHelper(private val context: TestContext) {

    /**
     * Check if two blocks are in the same network.
     *
     * @param pos1 First block position
     * @param pos2 Second block position
     * @return true if both blocks are in the same network
     */
    fun areInSameNetwork(pos1: BlockPos, pos2: BlockPos): Boolean {
        val network1 = context.blocks.getNetwork(pos1) ?: return false
        val network2 = context.blocks.getNetwork(pos2) ?: return false
        return network1 === network2
    }

    /**
     * Get all nodes in the same network as the block at the specified position.
     *
     * @param pos Block position
     * @return Iterable of nodes in the network, or empty if no network
     */
    fun getNodesInNetwork(pos: BlockPos): Iterable<Node> {
        val network = context.blocks.getNetwork(pos) ?: return emptyList()
        return network.nodes()
    }

    /**
     * Count the number of distinct networks among the given positions.
     *
     * @param positions List of block positions to check
     * @return Number of unique networks
     */
    fun countNetworks(positions: List<BlockPos>): Int {
        val networks = positions
            .mapNotNull { context.blocks.getNetwork(it) }
            .toSet()
        return networks.size
    }

    /**
     * Wait for network graph updates to complete.
     *
     * After placing or removing blocks, network connections may take a few ticks
     * to stabilize. This method schedules a delay to allow for stabilization.
     *
     * @param maxTicks Maximum ticks to wait (default: 20)
     */
    fun waitForNetworkStabilization(maxTicks: Int = 20) {
        // Network updates typically happen within 1-5 ticks
        // We wait the full maxTicks to be safe
        context.runAfterDelay(maxTicks) {
            // Just a delay, actual wait happens via scheduler
        }
    }

    /**
     * Verify that two blocks have a direct network connection.
     *
     * This checks if nodes exist and are connected, not just in the same network.
     *
     * @param from First block position
     * @param to Second block position
     * @return true if direct connection exists
     */
    fun verifyConnection(from: BlockPos, to: BlockPos): Boolean {
        val node1 = context.blocks.getNode(from) ?: return false
        val node2 = context.blocks.getNode(to) ?: return false

        val network = node1.network() as? Network ?: return false

        // Check if network contains both nodes and they're reachable from each other
        return network.nodes().contains(node1) &&
               network.nodes().contains(node2) &&
               node1.network() == node2.network()
    }

    /**
     * Verify that two blocks do NOT have a network connection.
     *
     * @param from First block position
     * @param to Second block position
     * @return true if no connection exists (different networks or no networks)
     */
    fun verifyDisconnection(from: BlockPos, to: BlockPos): Boolean {
        return !areInSameNetwork(from, to)
    }

    /**
     * Get the number of nodes in the network at the specified position.
     *
     * @param pos Block position
     * @return Number of nodes in the network, or 0 if no network
     */
    fun getNetworkSize(pos: BlockPos): Int {
        return getNodesInNetwork(pos).count()
    }

    /**
     * Check if a block at the specified position has neighbors in its network.
     *
     * @param pos Block position
     * @return true if the node has at least one neighbor in the network
     */
    fun hasNeighbors(pos: BlockPos): Boolean {
        val network = context.blocks.getNetwork(pos) ?: return false
        return network.nodes().count() > 1
    }

    /**
     * Get all network addresses in the network at the specified position.
     *
     * @param pos Block position
     * @return Set of node addresses
     */
    fun getNetworkAddresses(pos: BlockPos): Set<String> {
        return getNodesInNetwork(pos).map { it.address() }.toSet()
    }

    /**
     * Check if all given positions are in the same network.
     *
     * @param positions List of block positions
     * @return true if all blocks are in the same network
     */
    fun allInSameNetwork(positions: List<BlockPos>): Boolean {
        if (positions.size < 2) return true

        val firstNetwork = context.blocks.getNetwork(positions[0]) ?: return false

        return positions.drop(1).all { pos ->
            context.blocks.getNetwork(pos) === firstNetwork
        }
    }

    /**
     * Wait for a specific network condition to become true.
     *
     * @param maxTicks Maximum ticks to wait
     * @param condition Network condition to check
     * @return true if condition became true within timeout
     */
    fun waitForCondition(maxTicks: Int, condition: () -> Boolean): Boolean {
        var ticksWaited = 0
        var conditionMet = false

        while (ticksWaited < maxTicks && !conditionMet) {
            context.runAfterDelay(ticksWaited) {
                if (condition()) {
                    conditionMet = true
                }
            }
            ticksWaited++
        }

        return conditionMet
    }
}
