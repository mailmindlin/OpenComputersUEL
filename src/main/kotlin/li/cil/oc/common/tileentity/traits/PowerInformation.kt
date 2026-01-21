package li.cil.oc.common.tileentity.traits

import li.cil.oc.Settings
import li.cil.oc.server.PacketSender as ServerPacketSender
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

interface PowerInformation : TileEntityTrait {
    private var lastSentRatio = -1.0

    private var ticksUntilSync = 0

    abstract var globalBuffer: Double

    abstract var globalBufferSize: Double

    protected fun updatePowerInformation() {
        val ratio = if (globalBufferSize > 0) globalBuffer / globalBufferSize else 0.0
        if (shouldSync(ratio) || hasChangedSignificantly(ratio)) {
            lastSentRatio = ratio
            ServerPacketSender.sendPowerState(this)
        }
    }

    private fun hasChangedSignificantly(ratio: Double): Boolean = lastSentRatio < 0 || kotlin.math.abs(lastSentRatio - ratio) > (5.0 / 100.0)

    private fun shouldSync(ratio: Double): Boolean {
        ticksUntilSync -= 1
        if (ticksUntilSync <= 0) {
            ticksUntilSync = maxOf((100 / Settings.get.tickFrequency).toInt(), 1)
            return lastSentRatio != ratio
        }
        return false
    }

    // ----------------------------------------------------------------------- //

    companion object {
        private val GlobalBufferTag = Settings.namespace + "globalBuffer"
        private val GlobalBufferSizeTag = Settings.namespace + "globalBufferSize"
    }

    @SideOnly(Side.CLIENT)
    override fun readFromNBTForClient(nbt: NBTTagCompound) {
        super.readFromNBTForClient(nbt)
        globalBuffer = nbt.getDouble(GlobalBufferTag)
        globalBufferSize = nbt.getDouble(GlobalBufferSizeTag)
    }

    override fun writeToNBTForClient(nbt: NBTTagCompound) {
        super.writeToNBTForClient(nbt)
        lastSentRatio = if (globalBufferSize > 0) globalBuffer / globalBufferSize else 0.0
        nbt.setDouble(GlobalBufferTag, globalBuffer)
        nbt.setDouble(GlobalBufferSizeTag, globalBufferSize)
    }
}
