package li.cil.oc.common.tileentity

import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.tileentity.traits.PowerBalancer
import li.cil.oc.common.tileentity.traits.isServer
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import li.cil.oc.common.tileentity.traits.NotAnalyzable as TraitNotAnalyzable
import li.cil.oc.common.tileentity.traits.PowerBalancer as TraitPowerBalancer
import net.minecraftforge.common.util.Constants as NBTConstants

class PowerDistributor: TileEntityBase.TEEnvironmentBase(), TraitPowerBalancer, TraitNotAnalyzable {
    override fun node(): Node? = null

    override var globalBuffer: Double = 0.0
    override var globalBufferSize: Double = 0.0

    override val powerDelegate: PowerBalancer.Delegate = PowerBalancer.Delegate(this)

    private val nodes: Array<Connector> = Array(6) {
        Network.newNode(this, Visibility.None)!!
            .withConnector(Settings.get.bufferDistributor)
            .create()
    }

    override val isConnected: Boolean
        get() = nodes.any { node -> node.address() != null && node.network() != null }

    // ----------------------------------------------------------------------- //

    @SideOnly(Side.CLIENT)
    override fun canConnect(side: EnumFacing): Boolean = true

    override fun sidedNode(side: EnumFacing): Connector = nodes[side.ordinal]

    // ----------------------------------------------------------------------- //

    companion object {
        private const val ConnectorTag = Settings.namespace + "connector"
    }

    override fun readFromNBTForServer(nbt: NBTTagCompound) {
        super.readFromNBTForServer(nbt)
        this.powerDelegate.readFromNBTForServer(nbt)
        val tagList = nbt.getTagList(ConnectorTag, NBTConstants.NBT.TAG_COMPOUND)
        for (i in 0 until minOf(tagList.tagCount(), nodes.size)) {
            nodes[i].load(tagList.getCompoundTagAt(i))
        }
    }

    override fun writeToNBTForServer(nbt: NBTTagCompound) {
        super.writeToNBTForServer(nbt)
        // Side check for Waila (and other mods that may call this client side).
        if (isServer) {
            val tagList = NBTTagList()
            for (connector in nodes) {
                val connectorNbt = NBTTagCompound()
                connector.save(connectorNbt)
                tagList.appendTag(connectorNbt)
            }
            nbt.setTag(ConnectorTag, tagList)
        }
    }
}
