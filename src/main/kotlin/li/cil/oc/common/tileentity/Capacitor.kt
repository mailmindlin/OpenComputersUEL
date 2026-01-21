package li.cil.oc.common.tileentity

import li.cil.oc.Constants
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import net.minecraft.util.EnumFacing

open class Capacitor : traits.Environment(), DeviceInfo {
    // Start with maximum theoretical capacity, gets reduced after validation.
    // This is done so that we don't lose energy while loading.
    @JvmField
    val node: Node = api.Network.newNode(this, Visibility.Network)
        .withConnector(maxCapacity)
        .create()

    override fun getNode(): Node = node

    private val deviceInfo: Map<String, String> by lazy {
        mapOf(
            DeviceAttribute.Class to DeviceClass.Power,
            DeviceAttribute.Description to "Battery",
            DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
            DeviceAttribute.Product to "CapBank3x",
            DeviceAttribute.Capacity to maxCapacity.toString()
        )
    }

    override fun getDeviceInfo(): java.util.Map<String, String> = deviceInfo as java.util.Map<String, String>

    // ----------------------------------------------------------------------- //

    override fun dispose() {
        super.dispose()
        if (isServer) {
            indirectNeighbors.mapNotNull { coordinate ->
                if (world.isBlockLoaded(coordinate)) world.getTileEntity(coordinate) else null
            }.filterIsInstance<Capacitor>().forEach { capacitor ->
                capacitor.recomputeCapacity()
            }
        }
    }

    override fun onConnect(node: Node) {
        super.onConnect(node)
        if (node == this.node) {
            recomputeCapacity(updateSecondGradeNeighbors = true)
        }
    }

    // ----------------------------------------------------------------------- //

    @JvmOverloads
    fun recomputeCapacity(updateSecondGradeNeighbors: Boolean = false) {
        val adjacentCapacitors = EnumFacing.values().count { side ->
            val blockPos = pos.offset(side)
            world.isBlockLoaded(blockPos) && world.getTileEntity(blockPos) is Capacitor
        }

        val indirectCapacitors = indirectNeighbors.count { blockPos ->
            world.isBlockLoaded(blockPos) && (world.getTileEntity(blockPos) as? Capacitor)?.let { capacitor ->
                if (updateSecondGradeNeighbors) {
                    capacitor.recomputeCapacity()
                }
                true
            } ?: false
        }

        node.setLocalBufferSize(
            Settings.get.bufferCapacitor +
                Settings.get.bufferCapacitorAdjacencyBonus * adjacentCapacitors +
                Settings.get.bufferCapacitorAdjacencyBonus / 2 * indirectCapacitors
        )
    }

    private val indirectNeighbors
        get() = EnumFacing.values().map { pos.offset(it, 2) }

    protected open val maxCapacity: Double
        get() = Settings.get.bufferCapacitor + Settings.get.bufferCapacitorAdjacencyBonus * 9
}
