package li.cil.oc.common

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import li.cil.oc.Constants
import li.cil.oc.OpenComputers
import li.cil.oc.api.Items as ApiItems
import li.cil.oc.common.block.RobotAfterimage
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.blockExists
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.INetHandler
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fml.common.FMLCommonHandler
import java.io.DataInputStream
import java.io.InputStream
import java.util.zip.InflaterInputStream

abstract class PacketHandler {
    /** Top level dispatcher based on packet type. */
    protected fun onPacketData(handler: INetHandler, data: ByteBuf, player: EntityPlayer) {
        val thread = FMLCommonHandler.instance().getWorldThread(handler)
        if (thread.isCallingFromMinecraftThread) {
            process(data, player)
        } else {
            data.retain()
            thread.addScheduledTask {
                process(data, player)
                data.release()
            }
        }
    }

    private fun process(data: ByteBuf, player: EntityPlayer) {
        // Don't crash on badly formatted packets (may have been altered by a
        // malicious client, in which case we don't want to allow it to kill the
        // server like this). Just spam the log a bit... ;)
        var stream: InputStream? = null
        try {
            stream = ByteBufInputStream(data)
            if (stream.read() != 0) stream = InflaterInputStream(stream)
            dispatch(PacketParser(stream, player))
        } catch (e: Throwable) {
            OpenComputers.log.warn("Received a badly formatted packet.", e)
        } finally {
            stream?.close()
            // #3703 - neither 1.7.10 nor 1.12.2 release the packet ByteBuf by themselves
            if (data.refCnt() > 0) {
                data.release()
            }
        }

        // Avoid AFK kicks by marking players as non-idle when they send packets.
        // This will usually be stuff like typing while in screen GUIs.
        (player as? EntityPlayerMP)?.markPlayerActive()
    }

    /**
     * Gets the world for the specified dimension.
     *
     * For clients this returns the client's world if it is the specified
     * dimension; None otherwise. For the server it returns the world for the
     * specified dimension, if such a dimension exists; None otherwise.
     */
    protected abstract fun world(player: EntityPlayer, dimension: Int): World?

    protected abstract fun dispatch(p: PacketParser)

    protected inner class PacketParser(stream: InputStream, val player: EntityPlayer) : DataInputStream(stream) {
        val packetType: PacketType = PacketType(readByte())

        inline fun <reified T> getTileEntity(dimension: Int, x: Int, y: Int, z: Int): T? {
            val w = world(player, dimension) ?: return null
            val pos = BlockPos(x, y, z)
            if (!w.isBlockLoaded(pos))
                return null
            val t = w.getTileEntity(pos)
            if (t != null && T::class.java.isAssignableFrom(t.javaClass)) {
                return t as T
            }
            // In case a robot moved away before the packet arrived. This is
            // mostly used when the robot *starts* moving while the client sends
            // a request to the server.
            val afterimageBlock = ApiItems.get(Constants.BlockName.RobotAfterimage)?.block()
            if (afterimageBlock is RobotAfterimage) {
                val robot = afterimageBlock.findMovingRobot(w, pos)
                if (robot != null && T::class.java.isAssignableFrom(robot.proxy.javaClass)) {
                    return robot.proxy as T
                }
            }
        }

        inline fun <reified T> getEntity(dimension: Int, id: Int): T? {
            val w = world(player, dimension)
            if (w != null) {
                val e = w.getEntityByID(id)
                if (e != null && T::class.java.isAssignableFrom(e.javaClass)) {
                    return e as T
                }
            }
            return null
        }

        inline fun <reified T> readTileEntity(): T? {
            val dimension = readInt()
            val x = readInt()
            val y = readInt()
            val z = readInt()
            return getTileEntity(dimension, x, y, z)
        }

        inline fun <reified T> readEntity(): T? {
            val dimension = readInt()
            val id = readInt()
            return getEntity(dimension, id)
        }

        fun readDirection(): EnumFacing? {
            val id = readByte().toInt()
            return if (id < 0) null else EnumFacing.byIndex(id)
        }

        fun readItemStack(): ItemStack {
            val haveStack = readBoolean()
            return if (haveStack) {
                ItemStack(readNBT())
            } else {
                ItemStack.EMPTY
            }
        }

        fun readNBT(): NBTTagCompound? {
            val haveNbt = readBoolean()
            return if (haveNbt) {
                CompressedStreamTools.read(this)
            } else {
                null
            }
        }

        fun readMedium(): Int {
            val c0 = readUnsignedByte()
            val c1 = readUnsignedByte()
            val c2 = readUnsignedByte()
            return c0 or (c1 shl 8) or (c2 shl 16)
        }

        fun readPacketType(): PacketType = PacketType(readByte())
    }
}
