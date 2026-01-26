package li.cil.oc.integration.wrcbe

import codechicken.wirelessredstone.manager.RedstoneEther
import li.cil.oc.integration.util.WirelessRedstone.WirelessRedstoneSystem
import li.cil.oc.server.component.RedstoneWireless

object WirelessRedstoneCBE : WirelessRedstoneSystem {
    fun addTransmitter(rs: RedstoneWireless) {
        if (rs.wirelessOutput && rs.wirelessFrequency > 0) {
            RedstoneEther.server().addTransmittingDevice(rs)
        }
    }

    override fun removeTransmitter(rs: RedstoneWireless) {
        if (rs.wirelessFrequency > 0) {
            RedstoneEther.server().removeTransmittingDevice(rs)
        }
    }

    override fun addReceiver(rs: RedstoneWireless) {
        RedstoneEther.server().addReceivingDevice(rs)
        if (rs.wirelessFrequency > 0) {
            rs.wirelessInput = RedstoneEther.server().isFreqOn(rs.wirelessFrequency)
        }
    }

    override fun removeReceiver(rs: RedstoneWireless) {
        RedstoneEther.server().removeReceivingDevice(rs)
    }

    override fun updateOutput(rs: RedstoneWireless) {
        if (rs.wirelessOutput) {
            addTransmitter(rs)
        } else {
            removeTransmitter(rs)
        }
    }

    override fun getInput(rs: RedstoneWireless): Boolean = rs.wirelessInput
}
