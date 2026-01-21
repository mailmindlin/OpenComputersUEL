package li.cil.oc.server.component

import li.cil.oc.Constants
import li.cil.oc.api.Network
import li.cil.oc.api.driver.DeviceInfo.DeviceAttribute
import li.cil.oc.api.driver.DeviceInfo.DeviceClass
import li.cil.oc.api.internal.Tablet
import li.cil.oc.api.machine.Machine
import li.cil.oc.api.network.*
import li.cil.oc.util.BlockPosition
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.EnumFacing

sealed class UpgradeBarcodeReader(val host: EnvironmentHost) : ManagedEnvironmentKt(), DeviceInfoKt {
    override val node = Network.newNode(this, Visibility.Network)
        .withComponent("barcode_reader")
        .withConnector()
        .create()

    override val deviceInfo = mapOf(
        DeviceAttribute.Class to DeviceClass.Generic,
        DeviceAttribute.Description to "Barcode reader upgrade",
        DeviceAttribute.Vendor to Constants.DeviceInfo.DefaultVendor,
        DeviceAttribute.Product to "Readerizer Deluxe"
    )

    override fun onMessage(message: Message) {
        super.onMessage(message)
        if (message.name() == "tablet.use") {
            val sourceHost = message.source().host()
            if (sourceHost is Machine) {
                val machineHost = sourceHost.host()
                val data = message.data()
                if (machineHost is Tablet && data.size >= 8 &&
                    data[0] is NBTTagCompound && data[1] is ItemStack && data[2] is EntityPlayer &&
                    data[3] is BlockPosition && data[4] is EnumFacing &&
                    data[5] is Float && data[6] is Float && data[7] is Float
                ) {
                    val nbt = data[0] as NBTTagCompound
                    val blockPos = data[3] as BlockPosition
                    val side = data[4] as EnumFacing
                    val hitX = (data[5] as Float).toFloat()
                    val hitY = (data[6] as Float).toFloat()
                    val hitZ = (data[7] as Float).toFloat()
                    val player = data[2] as EntityPlayer

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
            }
        }
    }

    private fun processNodes(nodes: Array<Node>?, nbt: NBTTagCompound) {
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
