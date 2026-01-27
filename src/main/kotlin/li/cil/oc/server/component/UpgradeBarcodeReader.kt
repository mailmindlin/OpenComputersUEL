package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.network.*
import li.cil.oc.util.getTileEntity
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList

class UpgradeBarcodeReader(val host: EnvironmentHost) : ManagedEnvironmentKt(), DeviceInfo {
    override val node = newComponentConnector(Visibility.Network, "barcode_reader")

    private val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Generic,
        DeviceAttribute.Description to "Barcode reader upgrade",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "Readerizer Deluxe"
    )

    override fun getDeviceInfo() = deviceInfo

    override fun onMessage(message: Message) {
        super.onMessage(message)
        val message = TabletUseMessage.tryParse(message) ?: return
        val (nbt, _, player, blockPos, side, hitX, hitY, hitZ) = message

        when (val te = host.world.getTileEntity(blockPos)) {
            is Analyzable -> {
                val nodes = te.onAnalyze(player, side, hitX, hitY, hitZ)
                processNodes(nodes, nbt)
            }
            is SidedEnvironment -> {
                processNodes(arrayOf(te.sidedNode(side)), nbt)
            }
            is Environment -> {
                processNodes(arrayOf(te.node()), nbt)
            }
        }
    }

    private fun processNodes(nodes: Array<Node?>?, nbt: NBTTagCompound) {
        if (nodes != null) {
            val readerNBT = NBTTagList()

            for (node in nodes) {
                if (node != null) {
                    val nodeNBT = NBTTagCompound()
                    if (node is Component) {
                        nodeNBT.setString("type", node.name())
                    }

                    val address = node.address()
                    if (address != null && address.isNotEmpty()) {
                        nodeNBT.setString("address", address)
                    }

                    readerNBT.appendTag(nodeNBT)
                }
            }

            nbt.setTag("analyzed", readerNBT)
        }
    }
}
