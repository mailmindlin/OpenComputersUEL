package li.cil.oc.integration.tis3d

import li.cil.oc.Settings
import li.cil.oc.api.Network
import li.cil.oc.api.internal.Adapter
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.Component
import li.cil.oc.api.network.Environment
import li.cil.oc.api.network.Message
import li.cil.oc.api.network.Node
import li.cil.oc.api.network.Visibility
import li.cil.oc.util.Result
import li.cil.oc.util.result
import li.cil.tis3d.api.ManualAPI
import li.cil.tis3d.api.SerialAPI
import li.cil.tis3d.api.prefab.manual.ResourceContentProvider
import li.cil.tis3d.api.serial.SerialInterface
import li.cil.tis3d.api.serial.SerialInterfaceProvider
import li.cil.tis3d.api.serial.SerialProtocolDocumentationReference
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.*

object SerialInterfaceProviderAdapter : SerialInterfaceProvider {
    fun init() {
        ManualAPI.addProvider(ResourceContentProvider(Settings.resourceDomain, "doc/tis3d/"))
        SerialAPI.addProvider(this)
    }

    override fun getDocumentationReference(): SerialProtocolDocumentationReference =
        SerialProtocolDocumentationReference("OpenComputers Adapter", "protocols/opencomputersadapter.md")

    override fun worksWith(world: World, pos: BlockPos, side: EnumFacing): Boolean =
        world.getTileEntity(pos) is Adapter

    override fun interfaceFor(world: World, pos: BlockPos, side: EnumFacing): SerialInterface =
        SerialInterfaceAdapter(world.getTileEntity(pos) as Adapter)

    override fun isValid(world: World, pos: BlockPos, side: EnumFacing, serialInterface: SerialInterface): Boolean =
        when (serialInterface) {
            is SerialInterfaceAdapter -> serialInterface.tileEntity == world.getTileEntity(pos)
            else -> false
        }

    class SerialInterfaceAdapter(val tileEntity: Adapter) : Environment, SerialInterface {
        companion object {
            const val BufferCapacity = 128
        }

        private val readBuffer = LinkedList<Short>()
        private val writeBuffer = LinkedList<Short>()
        private var isReading = false

        // -----------------------------------------------------------------------

        override fun node() = node
        val node: Component? = Network.newNode(this, Visibility.Network)!!.withComponent("serial_port").create()

        override fun onMessage(message: Message) {}

        override fun onConnect(node: Node) {}

        override fun onDisconnect(node: Node) {}

        // -----------------------------------------------------------------------

        @Callback
        fun setReading(context: Context, args: Arguments): Result? {
            isReading = args.checkBoolean(0)
            return null
        }

        @Callback
        fun read(context: Context, args: Arguments): Result? {
            synchronized(readBuffer) {
                return if (readBuffer.isNotEmpty()) {
                    result(readBuffer.poll())
                } else {
                    null
                }
            }
        }

        @Callback
        fun write(context: Context, args: Arguments): Result {
            synchronized(writeBuffer) {
                return if (writeBuffer.size < BufferCapacity) {
                    writeBuffer.add(args.checkInteger(0).toShort())
                    result(true)
                } else {
                    result(false, "buffer full")
                }
            }
        }

        // -----------------------------------------------------------------------

        override fun canWrite(): Boolean = synchronized(readBuffer) {
            isReading && readBuffer.size < BufferCapacity
        }

        override fun write(value: Short) {
            synchronized(readBuffer) {
                readBuffer.add(value)
            }
        }

        override fun canRead(): Boolean {
            ensureConnected()
            return synchronized(writeBuffer) {
                writeBuffer.isNotEmpty()
            }
        }

        override fun peek(): Short = synchronized(writeBuffer) {
            writeBuffer.first
        }

        override fun skip() {
            synchronized(writeBuffer) {
                writeBuffer.poll()
            }
        }

        override fun reset() {
            synchronized(readBuffer) {
                synchronized(writeBuffer) {
                    readBuffer.clear()
                    writeBuffer.clear()
                    node!!.remove()
                }
            }
        }

        override fun readFromNBT(nbt: NBTTagCompound) {
            node!!.load(nbt)

            writeBuffer.clear()
            writeBuffer.addAll(nbt.getIntArray("writeBuffer").map { it.toShort() })
            readBuffer.clear()
            readBuffer.addAll(nbt.getIntArray("readBuffer").map { it.toShort() })
            isReading = nbt.getBoolean("isReading")
        }

        override fun writeToNBT(nbt: NBTTagCompound) {
            node!!.save(nbt)

            nbt.setIntArray("writeBuffer", writeBuffer.map { it.toInt() }.toIntArray())
            nbt.setIntArray("readBuffer", readBuffer.map { it.toInt() }.toIntArray())
            nbt.setBoolean("isReading", isReading)
        }

        private fun ensureConnected() {
            if (tileEntity.node()!!.network() != node!!.network()) {
                tileEntity.node()!!.connect(node)
            }
        }
    }
}
