package li.cil.oc.integration.waila

import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Connector
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.common.block.SimpleBlock
import li.cil.oc.common.tileentity.*
import li.cil.oc.common.tileentity.traits.NotAnalyzable
import li.cil.oc.util.setNewTagList
import mcp.mobius.waila.api.*
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagString
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.common.util.Constants.NBT

object BlockDataProvider : IWailaDataProvider {
    const val ConfigAddress = "oc.address"
    const val ConfigEnergy = "oc.energy"
    const val ConfigComponentName = "oc.componentName"

    @JvmStatic
    fun init(registrar: IWailaRegistrar) {
        registrar.registerBodyProvider(BlockDataProvider, SimpleBlock::class.java)

        registrar.registerNBTProvider(this, li.cil.oc.api.network.Environment::class.java)
        registrar.registerNBTProvider(this, li.cil.oc.api.network.SidedEnvironment::class.java)

        registrar.addConfig(OpenComputers.Name, ConfigAddress)
        registrar.addConfig(OpenComputers.Name, ConfigEnergy)
        registrar.addConfig(OpenComputers.Name, ConfigComponentName)
    }

    private fun stringIterableToNbt(strings: Iterable<String>): List<NBTBase> =
        strings.map { NBTTagString(it) }

    override fun getNBTData(
        player: EntityPlayerMP,
        tileEntity: TileEntity,
        tag: NBTTagCompound,
        world: World,
        pos: BlockPos
    ): NBTTagCompound {
        fun writeNode(node: Node?, tag: NBTTagCompound): NBTTagCompound {
            if (node != null && node.reachability() != Visibility.None && tileEntity !is NotAnalyzable) {
                if (node.address() != null) {
                    tag.setString("address", node.address())
                }
                if (node is Connector) {
                    tag.setInteger("buffer", node.localBuffer().toInt())
                    tag.setInteger("bufferSize", node.localBufferSize().toInt())
                }
                if (node is Component) {
                    tag.setString("componentName", node.name())
                }
            }
            return tag
        }

        when (tileEntity) {
            is li.cil.oc.api.network.SidedEnvironment -> {
                tag.setNewTagList(
                    "nodes",
                    EnumFacing.values().map { side ->
                        writeNode(tileEntity.sidedNode(side), NBTTagCompound())
                    }
                )
            }
            is li.cil.oc.api.network.Environment -> {
                writeNode(tileEntity.node(), tag)
            }
        }

        // Override sided info (show info on all sides).
        fun ignoreSidedness(node: Node?) {
            tag.removeTag("nodes")
            val nodeTag = writeNode(node, NBTTagCompound())
            tag.setNewTagList("nodes", EnumFacing.values().map { nodeTag })
        }

        when (tileEntity) {
            is Relay -> {
                tag.setDouble("signalStrength", tileEntity.strength)
                // this might be called by waila before the components have finished loading, thus the addresses may be null
                tag.setNewTagList(
                    "addresses",
                    stringIterableToNbt(
                        tileEntity.componentNodes
                            .filter { it.address() != null }
                            .map { it.address() }
                    )
                )
            }
            is Assembler -> {
                ignoreSidedness(tileEntity.node())
                if (tileEntity.isAssembling) {
                    tag.setDouble("progress", tileEntity.progress)
                    tag.setInteger("timeRemaining", tileEntity.timeRemaining)
                    tileEntity.output?.let { tag.setString("output", it.translationKey) }
                }
            }
            is Charger -> {
                tag.setDouble("chargeSpeed", tileEntity.chargeSpeed)
            }
            is DiskDrive -> {
                // Override address with file system address.
                tag.removeTag("address")
                tileEntity.filesystemNode?.let { writeNode(it, tag) }
            }
            is Hologram -> ignoreSidedness(tileEntity.node())
            is Keyboard -> ignoreSidedness(tileEntity.node())
            is Screen -> ignoreSidedness(tileEntity.node())
            is Rack -> {
                tag.removeTag("nodes")
                // TODO: Uncomment when needed
                //tag.setNewTagList("servers", stringIterableToNbt(tileEntity.servers.map { it?.node?.address() ?: "" }))
                //tag.setByteArray("sideIndexes", EnumFacing.values().map { side -> tileEntity.sides.indexOfFirst { it.contains(side) }.toByte() }.toByteArray())
            }
        }

        return tag
    }

    override fun getWailaBody(
        stack: ItemStack,
        tooltip: MutableList<String>,
        accessor: IWailaDataAccessor,
        config: IWailaConfigHandler
    ): MutableList<String> {
        val tag = accessor.nbtData
        if (tag == null || tag.isEmpty) return tooltip

        when (accessor.tileEntity) {
            is Relay -> {
                val address = tag.getTagList("addresses", NBT.TAG_STRING).getStringTagAt(accessor.side.ordinal)
                val signalStrength = tag.getDouble("signalStrength")
                if (config.getConfig(ConfigAddress)) {
                    tooltip.add(Localization.Analyzer.Address(address).unformattedText)
                }
                tooltip.add(Localization.Analyzer.WirelessStrength(signalStrength).unformattedText)
            }
            is Assembler -> {
                if (tag.hasKey("progress")) {
                    val progress = tag.getDouble("progress")
                    val timeRemaining = formatTime(tag.getInteger("timeRemaining"))
                    tooltip.add(Localization.Assembler.Progress(progress, timeRemaining))
                    if (tag.hasKey("output")) {
                        val output = tag.getString("output")
                        tooltip.add("Building: " + Localization.localizeImmediately(output))
                    }
                }
            }
            is Charger -> {
                val chargeSpeed = tag.getDouble("chargeSpeed")
                tooltip.add(Localization.Analyzer.ChargerSpeed(chargeSpeed).unformattedText)
            }
            is Rack -> {
                // TODO: Uncomment when needed
                //val servers = tag.getTagList("servers", NBT.TAG_STRING).map { (it as NBTTagString).string }.toTypedArray()
                //val hitPos = accessor.mop.hitVec
                //val address = tileEntity.slotAt(accessor.side, (hitPos.x - accessor.mop.blockPos.x).toFloat(), (hitPos.y - accessor.mop.blockPos.y).toFloat(), (hitPos.z - accessor.mop.blockPos.z).toFloat())?.let { slot ->
                //    servers[slot]
                //} ?: tag.getByteArray("sideIndexes").map { index -> if (index >= 0) servers[index.toInt()] else "" }[tileEntity.toLocal(accessor.side).ordinal]
                //if (address.isNotEmpty() && config.getConfig(ConfigAddress)) {
                //    tooltip.add(Localization.Analyzer.Address(address).unformattedText)
                //}
            }
        }

        fun readNode(tag: NBTTagCompound) {
            if (config.getConfig(ConfigAddress) && tag.hasKey("address")) {
                val address = tag.getString("address")
                if (address.isNotEmpty()) {
                    tooltip.add(Localization.Analyzer.Address(address).unformattedText)
                }
            }
            if (config.getConfig(ConfigEnergy) && tag.hasKey("buffer") && tag.hasKey("bufferSize")) {
                val buffer = tag.getInteger("buffer")
                val bufferSize = tag.getInteger("bufferSize")
                if (bufferSize > 0) {
                    tooltip.add(Localization.Analyzer.StoredEnergy("$buffer/$bufferSize").unformattedText)
                }
            }
            if (config.getConfig(ConfigComponentName) && tag.hasKey("componentName")) {
                val componentName = tag.getString("componentName")
                if (componentName.isNotEmpty()) {
                    tooltip.add(Localization.Analyzer.ComponentName(componentName).unformattedText)
                }
            }
        }

        when (accessor.tileEntity) {
            is li.cil.oc.api.network.SidedEnvironment -> {
                readNode(tag.getTagList("nodes", NBT.TAG_COMPOUND).getCompoundTagAt(accessor.side.ordinal))
            }
            is li.cil.oc.api.network.Environment -> {
                readNode(tag)
            }
        }

        return tooltip
    }

    override fun getWailaStack(accessor: IWailaDataAccessor, config: IWailaConfigHandler): ItemStack =
        accessor.stack

    override fun getWailaHead(
        stack: ItemStack,
        tooltip: MutableList<String>,
        accessor: IWailaDataAccessor,
        config: IWailaConfigHandler
    ): MutableList<String> = tooltip

    override fun getWailaTail(
        stack: ItemStack,
        tooltip: MutableList<String>,
        accessor: IWailaDataAccessor,
        config: IWailaConfigHandler
    ): MutableList<String> = tooltip

    private fun formatTime(seconds: Int): String {
        // Assembly times should not / rarely exceed one hour, so this is good enough.
        return if (seconds < 60) {
            String.format("0:%02d", seconds)
        } else {
            String.format("%d:%02d", seconds / 60, seconds % 60)
        }
    }
}
