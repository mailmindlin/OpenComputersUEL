package li.cil.oc.common

import io.netty.buffer.Unpooled
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api.network.EnvironmentHost
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import net.minecraft.world.WorldServer
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

abstract class PacketBuilder(stream: OutputStream) : DataOutputStream(stream) {
    fun writeTileEntity(t: TileEntity) {
        writeInt(t.world.provider.dimension)
        writeInt(t.pos.x)
        writeInt(t.pos.y)
        writeInt(t.pos.z)
    }

    fun writeEntity(e: Entity) {
        writeInt(e.world.provider.dimension)
        writeInt(e.entityId)
    }

    fun writeDirection(d: EnumFacing?) {
        if (d != null) {
            writeByte(d.ordinal)
        } else {
            writeByte(-1)
        }
    }

    fun writeItemStack(stack: ItemStack) {
        val haveStack = !stack.isEmpty && stack.count > 0
        writeBoolean(haveStack)
        if (haveStack) {
            writeNBT(stack.writeToNBT(NBTTagCompound()))
        }
    }

    fun writeNBT(nbt: NBTTagCompound?) {
        val haveNbt = nbt != null
        writeBoolean(haveNbt)
        if (haveNbt) {
            CompressedStreamTools.write(nbt, this)
        }
    }

    fun writeMedium(v: Int) {
        writeByte(v and 0xFF)
        writeByte((v shr 8) and 0xFF)
        writeByte((v shr 16) and 0xFF)
    }

    fun writePacketType(pt: PacketType) {
        writeByte(pt.id)
    }

    fun sendToAllPlayers() {
        OpenComputers.channel.sendToAll(packet)
    }

    @JvmOverloads
    fun sendToPlayersNearEntity(e: Entity, range: Double? = null) {
        sendToNearbyPlayers(e.entityWorld, e.posX, e.posY, e.posZ, range)
    }

    @JvmOverloads
    fun sendToPlayersNearHost(host: EnvironmentHost, range: Double? = null) {
        when (host) {
            is TileEntity -> sendToPlayersNearTileEntity(host, range)
            else -> sendToNearbyPlayers(host.world(), host.xPosition(), host.yPosition(), host.zPosition(), range)
        }
    }

    @JvmOverloads
    fun sendToPlayersNearTileEntity(t: TileEntity, range: Double? = null) {
        val world = t.world
        if (world is WorldServer) {
            val chunkX = t.pos.x shr 4
            val chunkZ = t.pos.z shr 4

            val manager = FMLCommonHandler.instance().minecraftServerInstance.playerList
            var maxPacketRange = range ?: ((manager.viewDistance + 1) * 16.0)
            val maxPacketRangeConfig = Settings.get.maxNetworkClientPacketDistance
            if (maxPacketRangeConfig > 0.0) {
                maxPacketRange = minOf(maxPacketRange, maxPacketRangeConfig)
            }
            val maxPacketRangeSq = maxPacketRange * maxPacketRange

            for (entity in world.playerEntities) {
                val player = entity as? EntityPlayerMP ?: continue
                if (world.playerChunkMap.isPlayerWatchingChunk(player, chunkX, chunkZ)) {
                    if (player.getDistanceSq(t.pos.x + 0.5, t.pos.y + 0.5, t.pos.z + 0.5) <= maxPacketRangeSq) {
                        sendToPlayer(player)
                    }
                }
            }
        } else {
            sendToNearbyPlayers(t.world, t.pos.x + 0.5, t.pos.y + 0.5, t.pos.z + 0.5, range)
        }
    }

    fun sendToNearbyPlayers(world: World, x: Double, y: Double, z: Double, range: Double?) {
        val dimension = world.provider.dimension
        val server = FMLCommonHandler.instance().minecraftServerInstance
        val manager = server.playerList

        var maxPacketRange = range ?: ((manager.viewDistance + 1) * 16.0)
        val maxPacketRangeConfig = Settings.get.maxNetworkClientPacketDistance
        if (maxPacketRangeConfig > 0.0) {
            maxPacketRange = minOf(maxPacketRange, maxPacketRangeConfig)
        }
        val maxPacketRangeSq = maxPacketRange * maxPacketRange

        for (player in manager.players) {
            if (player.dimension == dimension) {
                if (player.getDistanceSq(x, y, z) <= maxPacketRangeSq) {
                    sendToPlayer(player)
                }
            }
        }
    }

    fun sendToPlayer(player: EntityPlayerMP) {
        OpenComputers.channel.sendTo(packet, player)
    }

    fun sendToServer() {
        OpenComputers.channel.sendToServer(packet)
    }

    protected abstract val packet: FMLProxyPacket
}

// Necessary to keep track of the GZIP stream.
abstract class PacketBuilderBase<T : OutputStream>(protected val stream: T) : PacketBuilder(BufferedOutputStream(stream))

class SimplePacketBuilder(val packetType: PacketType) : PacketBuilderBase<ByteArrayOutputStream>(PacketBuilderCompanion.newData(compressed = false)) {
    init {
        writeByte(packetType.id)
    }

    override val packet: FMLProxyPacket
        get() {
            flush()
            return FMLProxyPacket(PacketBuffer(Unpooled.wrappedBuffer(stream.toByteArray())), "OpenComputers")
        }
}

class CompressedPacketBuilder @JvmOverloads constructor(
    val packetType: PacketType,
    private val data: ByteArrayOutputStream = PacketBuilderCompanion.newData(compressed = true)
) : PacketBuilderBase<DeflaterOutputStream>(DeflaterOutputStream(data, Deflater(Deflater.BEST_SPEED))) {
    init {
        writeByte(packetType.id)
    }

    override val packet: FMLProxyPacket
        get() {
            flush()
            stream.finish()
            return FMLProxyPacket(PacketBuffer(Unpooled.wrappedBuffer(data.toByteArray())), "OpenComputers")
        }
}

object PacketBuilderCompanion {
    @JvmStatic
    fun newData(compressed: Boolean): ByteArrayOutputStream {
        val data = ByteArrayOutputStream()
        data.write(if (compressed) 1 else 0)
        return data
    }
}
