package li.cil.oc.common.tileentity.traits.power

import li.cil.oc.Settings
import li.cil.oc.api.network.Connector
import li.cil.oc.common.tileentity.traits.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

interface Common {
    val isClient: Boolean
    val isServer: Boolean

    @SideOnly(Side.CLIENT)
    fun hasConnector(side: EnumFacing): Boolean = false

    fun connector(side: EnumFacing): Connector? = null

    // ----------------------------------------------------------------------- //

    val energyThroughput: Double

    fun tryAllSides(provider: (Double, EnumFacing) -> Double, fromOther: (Double) -> Double, toOther: (Double) -> Double) {
        // We make sure to only call this every `Settings.get.tickFrequency` ticks,
        // but our throughput is per tick, so multiply this up for actual budget.
        var budget = energyThroughput * Settings.get.tickFrequency
        for (side in EnumFacing.values()) {
            val demand = toOther(minOf(budget, globalDemand(side)))
            if (demand > 1) {
                val energy = fromOther(provider(demand, side))
                if (energy > 0) {
                    budget -= tryChangeBuffer(side, energy)
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //

    fun canConnectPower(side: EnumFacing): Boolean =
        !Settings.get.ignorePower && (if (isClient) hasConnector(side) else connector(side) != null)

    /**
     * Tries to inject the specified amount of energy into the buffer via the specified side.
     *
     * @param side the side to change the buffer through.
     * @param amount the amount to change the buffer by.
     * @param doReceive whether to actually inject energy or only simulate it.
     * @return the amount of energy that was actually injected.
     */
    fun tryChangeBuffer(side: EnumFacing, amount: Double, doReceive: Boolean = true): Double {
        if (isClient || Settings.get.ignorePower) return 0.0
        val node = connector(side)
        return if (node != null) {
            val cappedAmount = maxOf(0.0, minOf(minOf(energyThroughput, amount), globalDemand(side)))
            if (doReceive) cappedAmount - node.changeBuffer(cappedAmount)
            else cappedAmount
        } else 0.0
    }

    fun globalBuffer(side: EnumFacing): Double {
        if (isClient) return 0.0
        val node = connector(side)
        return node?.globalBuffer() ?: 0.0
    }

    fun globalBufferSize(side: EnumFacing): Double {
        if (isClient) return 0.0
        val node = connector(side)
        return node?.globalBufferSize() ?: 0.0
    }

    fun globalDemand(side: EnumFacing): Double = maxOf(0.0, minOf(energyThroughput, globalBufferSize(side) - globalBuffer(side)))
}
