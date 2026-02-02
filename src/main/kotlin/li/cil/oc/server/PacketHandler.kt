package li.cil.oc.server

import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.api.internal.Server
import li.cil.oc.api.machine.Machine
import li.cil.oc.common.Achievement
import li.cil.oc.common.PacketType
import li.cil.oc.common.component.TextBuffer
import li.cil.oc.common.container.Player
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.item.Delegator
import li.cil.oc.common.item.Tablet
import li.cil.oc.common.item.data.DriveData
import li.cil.oc.common.item.traits.FileSystemLike
import li.cil.oc.common.tileentity.*
import li.cil.oc.common.tileentity.traits.Computer
import li.cil.oc.common.tileentity.traits.position
import li.cil.oc.common.PacketHandler as CommonPacketHandler
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.NetHandlerPlayServer
import net.minecraft.util.EnumHand
import net.minecraft.world.World
import net.minecraftforge.common.DimensionManager
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ServerCustomPacketEvent
import org.apache.logging.log4j.MarkerManager

object PacketHandler : CommonPacketHandler() {
    private val securityMarker = MarkerManager.getMarker("SuspiciousPackets")

    private fun logForgedPacket(player: EntityPlayerMP) {
        OpenComputers.log.warn(securityMarker, "Player {} tried to send GUI packets without opening them", player.gameProfile)
    }

    @SubscribeEvent
    fun onPacket(e: ServerCustomPacketEvent) {
        onPacketData(e.manager.netHandler, e.packet.payload(), (e.handler as NetHandlerPlayServer).player)
    }

    override fun world(player: EntityPlayer?, dimension: Int): World? =
        DimensionManager.getWorld(dimension)

    override fun dispatch(p: PacketParser) {
        when (p.packetType) {
            PacketType.ComputerPower -> onComputerPower(p)
            PacketType.CopyToAnalyzer -> onCopyToAnalyzer(p)
            PacketType.DriveLock -> onDriveLock(p)
            PacketType.DriveMode -> onDriveMode(p)
            PacketType.DronePower -> onDronePower(p)
            PacketType.KeyDown -> onKeyDown(p)
            PacketType.KeyUp -> onKeyUp(p)
            PacketType.Clipboard -> onClipboard(p)
            PacketType.MachineItemStateRequest -> onMachineItemStateRequest(p)
            PacketType.MouseClickOrDrag -> onMouseClick(p)
            PacketType.MouseScroll -> onMouseScroll(p)
            PacketType.MouseUp -> onMouseUp(p)
            PacketType.PetVisibility -> onPetVisibility(p)
            PacketType.RackMountableMapping -> onRackMountableMapping(p)
            PacketType.RackRelayState -> onRackRelayState(p)
            PacketType.RobotAssemblerStart -> onRobotAssemblerStart(p)
            PacketType.RobotStateRequest -> onRobotStateRequest(p)
            PacketType.ServerPower -> onServerPower(p)
            PacketType.TextBufferInit -> onTextBufferInit(p)
            PacketType.WaypointLabel -> onWaypointLabel(p)
            else -> { } // Invalid packet.
        }
    }

    fun onComputerPower(p: PacketParser) {
        val entity = p.readTileEntity<Computer>()
        val setPower = p.readBoolean()
        val player = p.player
        if (player is EntityPlayerMP) {
            when (val container = player.openContainer) {
                is Player<*> -> {
                    val computer = container.otherInventory
                    if (computer is Computer && entity != null && entity.position == computer.position) {
                        trySetComputerPower(computer.machine!!, setPower, player)
                    } else {
                        logForgedPacket(player)
                    }
                }
                else -> logForgedPacket(player)
            }
        }
    }

    fun onServerPower(p: PacketParser) {
        val entity = p.readTileEntity<Rack>()
        val index = p.readInt()
        val readServer = entity?.getMountable(index) as? Server ?: return
        val setPower = p.readBoolean()
        val player = p.player
        if (player is EntityPlayerMP) {
            when (val container = player.openContainer) {
                is li.cil.oc.common.container.Server -> {
                    val server = container.server
                    if (server != null && server == readServer) {
                        trySetComputerPower(server.machine(), setPower, player)
                    } else {
                        logForgedPacket(player)
                    }
                }
                else -> logForgedPacket(player)
            }
        }
    }

    fun onCopyToAnalyzer(p: PacketParser) {
        val text = p.readUTF()
        val line = p.readInt()
        val buffer = ComponentTracker.get(p.player!!.world, text)
        if (buffer is TextBuffer) {
            buffer.copyToAnalyzer(line, p.player)
        }
    }

    fun onDriveLock(p: PacketParser) {
        val player = p.player
        if (player is EntityPlayerMP) {
            val heldItem = player.getHeldItem(EnumHand.MAIN_HAND)
            val subItem = Delegator.subItem(heldItem)
            if (subItem is FileSystemLike) {
                DriveData.lock(heldItem, player)
            }
        }
    }

    fun onDriveMode(p: PacketParser) {
        val unmanaged = p.readBoolean()
        val player = p.player
        if (player is EntityPlayerMP) {
            val heldItem = player.getHeldItem(EnumHand.MAIN_HAND)
            val subItem = Delegator.subItem(heldItem)
            if (subItem is FileSystemLike) {
                DriveData.setUnmanaged(heldItem, unmanaged)
            }
        }
    }

    fun onDronePower(p: PacketParser) {
        val entity = p.readEntity<Drone>()
        val power = p.readBoolean()
        val player = p.player
        if (player is EntityPlayerMP) {
            val container = player.openContainer
            if (container is li.cil.oc.common.container.Drone && entity != null && container.drone == entity) {
                val drone = container.drone
                if (power)
                    drone.preparePowerUp()
                trySetComputerPower(drone.machine!!, power, player)
            } else {
                logForgedPacket(player)
            }
        }
    }

    private fun trySetComputerPower(computer: Machine, value: Boolean, player: EntityPlayerMP) {
        if (computer.canInteract(player.name)) {
            if (value) {
                if (!computer.isPaused) {
                    computer.start()
                    computer.lastError()?.let { message ->
                        player.sendMessage(Localization.Analyzer.LastError(message))
                    }
                }
            } else {
                computer.stop()
            }
        }
    }

    fun onKeyDown(p: PacketParser) {
        val address = p.readUTF()
        val key = p.readChar()
        val code = p.readInt()
        val buffer = ComponentTracker.get(p.player!!.world, address)
        if (buffer is li.cil.oc.api.internal.TextBuffer) {
            buffer.keyDown(key, code, p.player)
        }
    }

    fun onKeyUp(p: PacketParser) {
        val address = p.readUTF()
        val key = p.readChar()
        val code = p.readInt()
        val buffer = ComponentTracker.get(p.player!!.world, address)
        if (buffer is li.cil.oc.api.internal.TextBuffer) {
            buffer.keyUp(key, code, p.player)
        }
    }

    fun onClipboard(p: PacketParser) {
        val address = p.readUTF()
        val copy = p.readUTF()
        val buffer = ComponentTracker.get(p.player!!.world, address)
        if (buffer is li.cil.oc.api.internal.TextBuffer) {
            buffer.clipboard(copy, p.player)
        }
    }

    fun onMouseClick(p: PacketParser) {
        val address = p.readUTF()
        val x = p.readFloat()
        val y = p.readFloat()
        val dragging = p.readBoolean()
        val button = p.readByte()
        val buffer = ComponentTracker.get(p.player!!.world, address)
        if (buffer is li.cil.oc.api.internal.TextBuffer) {
            val player = p.player
            val x = x.toDouble()
            val y = y.toDouble()
            if (dragging) buffer.mouseDrag(x, y, button.toInt(), player)
            else buffer.mouseDown(x, y, button.toInt(), player)
        }
    }

    fun onMouseUp(p: PacketParser) {
        val address = p.readUTF()
        val x = p.readFloat()
        val y = p.readFloat()
        val button = p.readByte()
        val buffer = ComponentTracker.get(p.player!!.world, address)
        if (buffer is li.cil.oc.api.internal.TextBuffer) {
            val player = p.player
            buffer.mouseUp(x.toDouble(), y.toDouble(), button.toInt(), player)
        }
    }

    fun onMouseScroll(p: PacketParser) {
        val address = p.readUTF()
        val x = p.readFloat()
        val y = p.readFloat()
        val button = p.readByte()
        val buffer = ComponentTracker.get(p.player!!.world, address)
        if (buffer is li.cil.oc.api.internal.TextBuffer) {
            val player = p.player
            buffer.mouseScroll(x.toDouble(), y.toDouble(), button.toInt(), player)
        }
    }

    fun onPetVisibility(p: PacketParser) {
        val value = p.readBoolean()
        val player = p.player
        if (player is EntityPlayerMP) {
            val changed = if (value) {
                PetVisibility.hidden.remove(player.name)
            } else {
                PetVisibility.hidden.add(player.name)
            }
            if (changed) {
                // Something changed.
                PacketSender.sendPetVisibility(player.name)
            }
        }
    }

    fun onRackMountableMapping(p: PacketParser) {
        val entity = p.readTileEntity<Rack>()
        val mountableIndex = p.readInt()
        val nodeIndex = p.readInt()
        val side = p.readDirection()
        val player = p.player
        if (player is EntityPlayerMP) {
            val container = player.openContainer
            if (container is li.cil.oc.common.container.Rack && entity != null && entity == container.rack) {
                if (container.rack.isUsableByPlayer(player))
                    container.rack.connect(mountableIndex, nodeIndex - 1, side)
            } else {
                logForgedPacket(player)
            }
        }
    }

    fun onRackRelayState(p: PacketParser) {
        val entity = p.readTileEntity<Rack>()
        val enabled = p.readBoolean()
        if (entity != null) {
            val player = p.player
            if (player is EntityPlayerMP && entity.isUsableByPlayer(player)) {
                entity.isRelayEnabled = enabled
            }
        }
    }

    fun onRobotAssemblerStart(p: PacketParser) {
        val entity = p.readTileEntity<Assembler>()
        if (entity != null) {
            val player = p.player!!
            val isCreative = player is EntityPlayerMP && player.capabilities.isCreativeMode
            if (entity.start(isCreative)) {
                entity.output?.let { stack ->
                    Achievement.onAssemble(stack, player)
                }
            }
        }
    }

    fun onRobotStateRequest(p: PacketParser) {
        val proxy = p.readTileEntity<RobotProxy>()
        if (proxy != null) {
            proxy.world.notifyBlockUpdate(proxy.pos, proxy.world.getBlockState(proxy.pos), proxy.world.getBlockState(proxy.pos), 3)
        }
    }

    fun onMachineItemStateRequest(p: PacketParser) {
        val player = p.player
        if (player is EntityPlayerMP) {
            val stack = p.readItemStack()
            PacketSender.sendMachineItemState(player, stack, Tablet.get(stack, p.player).machine!!.isRunning)
        }
    }

    fun onTextBufferInit(p: PacketParser) {
        val address = p.readUTF()
        val player = p.player
        if (player is EntityPlayerMP) {
            val buffer = ComponentTracker.get(p.player.world, address)
            if (buffer is TextBuffer) {
                val host = buffer.host
                if (host !is Screen || host.isOrigin()) {
                    val nbt = NBTTagCompound()
                    buffer.data.save(nbt)
                    nbt.setInteger("maxWidth", buffer.maximumWidth)
                    nbt.setInteger("maxHeight", buffer.maximumHeight)
                    nbt.setInteger("viewportWidth", buffer.viewportWidth)
                    nbt.setInteger("viewportHeight", buffer.viewportHeight)
                    PacketSender.sendTextBufferInit(address, nbt, player)
                }
            }
        }
    }

    fun onWaypointLabel(p: PacketParser) {
        val entity = p.readTileEntity<Waypoint>()
        val label = p.readUTF().take(32)
        if (entity != null) {
            val player = p.player
            if (player is EntityPlayerMP && player.getDistanceSq(entity.x + 0.5, entity.y + 0.5, entity.z + 0.5) <= 64) {
                if (label != entity.label) {
                    entity.label = label
                    PacketSender.sendWaypointLabel(entity)
                }
            }
        }
    }
}
