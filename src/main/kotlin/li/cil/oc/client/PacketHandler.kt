package li.cil.oc.client

import java.io.EOFException

import li.cil.oc.Localization
import li.cil.oc.OpenComputers
import li.cil.oc.Settings
import li.cil.oc.api
import li.cil.oc.api.event.FileSystemAccessEvent
import li.cil.oc.api.event.NetworkActivityEvent
import li.cil.oc.client.renderer.PetRenderer
import li.cil.oc.common.Loot
import li.cil.oc.common.PacketType
import li.cil.oc.common.component
import li.cil.oc.common.container
import li.cil.oc.common.item.Tablet
import li.cil.oc.common.item.TabletWrapper
import li.cil.oc.common.nanomachines.ControllerImpl
import li.cil.oc.common.tileentity.*
import li.cil.oc.common.tileentity.traits.*
import li.cil.oc.common.PacketHandler as CommonPacketHandler
import li.cil.oc.integration.Mods
import li.cil.oc.integration.jei.ModJEI
import li.cil.oc.util.Audio
import li.cil.oc.util.ExtendedWorld.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import net.minecraft.util.SoundEvent
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Optional
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent
import org.lwjgl.input.Keyboard

object PacketHandler : CommonPacketHandler() {
    @SubscribeEvent
    fun onPacket(e: ClientCustomPacketEvent) {
        onPacketData(e.manager.netHandler, e.packet.payload(), Minecraft.getMinecraft().player)
    }

    override fun world(player: EntityPlayer, dimension: Int): World? {
        val world = player.world
        return if (world.provider.dimension == dimension) world else null
    }

    override fun dispatch(p: PacketParser) {
        when (p.packetType) {
            PacketType.AdapterState -> onAdapterState(p)
            PacketType.Analyze -> onAnalyze(p)
            PacketType.ChargerState -> onChargerState(p)
            PacketType.ClientLog -> onClientLog(p)
            PacketType.Clipboard -> onClipboard(p)
            PacketType.ColorChange -> onColorChange(p)
            PacketType.MachineItemStateResponse -> onMachineItemStateResponse(p)
            PacketType.ComputerState -> onComputerState(p)
            PacketType.ComputerUserList -> onComputerUserList(p)
            PacketType.ContainerUpdate -> onContainerUpdate(p)
            PacketType.DisassemblerActiveChange -> onDisassemblerActiveChange(p)
            PacketType.FileSystemActivity -> onFileSystemActivity(p)
            PacketType.FloppyChange -> onFloppyChange(p)
            PacketType.HologramArea -> onHologramArea(p)
            PacketType.HologramClear -> onHologramClear(p)
            PacketType.HologramColor -> onHologramColor(p)
            PacketType.HologramPowerChange -> onHologramPowerChange(p)
            PacketType.HologramRotation -> onHologramRotation(p)
            PacketType.HologramRotationSpeed -> onHologramRotationSpeed(p)
            PacketType.HologramScale -> onHologramScale(p)
            PacketType.HologramTranslation -> onHologramPositionOffsetY(p)
            PacketType.HologramValues -> onHologramValues(p)
            PacketType.LootDisk -> onLootDisk(p)
            PacketType.CyclingDisk -> onCyclingDisk(p)
            PacketType.NanomachinesConfiguration -> onNanomachinesConfiguration(p)
            PacketType.NanomachinesInputs -> onNanomachinesInputs(p)
            PacketType.NanomachinesPower -> onNanomachinesPower(p)
            PacketType.NetSplitterState -> onNetSplitterState(p)
            PacketType.NetworkActivity -> onNetworkActivity(p)
            PacketType.ParticleEffect -> onParticleEffect(p)
            PacketType.PetVisibility -> onPetVisibility(p)
            PacketType.PowerState -> onPowerState(p)
            PacketType.PrinterState -> onPrinterState(p)
            PacketType.RackInventory -> onRackInventory(p)
            PacketType.RackMountableData -> onRackMountableData(p)
            PacketType.RaidStateChange -> onRaidStateChange(p)
            PacketType.RedstoneState -> onRedstoneState(p)
            PacketType.RobotAnimateSwing -> onRobotAnimateSwing(p)
            PacketType.RobotAnimateTurn -> onRobotAnimateTurn(p)
            PacketType.RobotAssemblingState -> onRobotAssemblingState(p)
            PacketType.RobotInventoryChange -> onRobotInventoryChange(p)
            PacketType.RobotLightChange -> onRobotLightChange(p)
            PacketType.RobotMove -> onRobotMove(p)
            PacketType.RobotNameChange -> onRobotNameChange(p)
            PacketType.RobotSelectedSlotChange -> onRobotSelectedSlotChange(p)
            PacketType.RotatableState -> onRotatableState(p)
            PacketType.SwitchActivity -> onSwitchActivity(p)
            PacketType.TextBufferInit -> onTextBufferInit(p)
            PacketType.TextBufferPowerChange -> onTextBufferPowerChange(p)
            PacketType.TextBufferMulti -> onTextBufferMulti(p)
            PacketType.ScreenTouchMode -> onScreenTouchMode(p)
            PacketType.SoundEffect -> onSoundEffect(p)
            PacketType.Sound -> onSound(p)
            PacketType.SoundPattern -> onSoundPattern(p)
            PacketType.TransposerActivity -> onTransposerActivity(p)
            PacketType.WaypointLabel -> onWaypointLabel(p)
            else -> {} // Invalid packet.
        }
    }

    fun onAdapterState(p: PacketParser) {
        val t = p.readTileEntity<Adapter>()
        if (t != null) {
            t.openSides = t.uncompressSides(p.readByte())
            t.world.notifyBlockUpdate(t.pos)
        }
    }

    fun onAnalyze(p: PacketParser) {
        val address = p.readUTF()
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)) {
            GuiScreen.setClipboardString(address)
            p.player.sendMessage(Localization.Analyzer.AddressCopied)
        }
    }

    fun onChargerState(p: PacketParser) {
        val t = p.readTileEntity<Charger>()
        if (t != null) {
            t.chargeSpeed = p.readDouble()
            t.hasPower = p.readBoolean()
            t.world.notifyBlockUpdate(t.position)
        }
    }

    fun onClientLog(p: PacketParser) {
        OpenComputers.log.info(p.readUTF())
    }

    fun onClipboard(p: PacketParser) {
        GuiScreen.setClipboardString(p.readUTF())
    }

    fun onColorChange(p: PacketParser) {
        val t = p.readTileEntity<Colored>()
        if (t != null) {
            t.setColor(p.readInt())
            t.world.notifyBlockUpdate(t.position)
        }
    }

    fun onMachineItemStateResponse(p: PacketParser) {
        val stack = p.readItemStack()
        val running = p.readBoolean()
        val wrapper = Tablet.Client.get(stack, p.player)

        wrapper.data.isRunning = running
        wrapper.isDirty = false
    }

    fun onComputerState(p: PacketParser) {
        val t = p.readTileEntity<Computer>()
        if (t != null) {
            t.setRunning(p.readBoolean())
            t.hasErrored = p.readBoolean()
        }
    }

    fun onComputerUserList(p: PacketParser) {
        val t = p.readTileEntity<Computer>()
        if (t != null) {
            val count = p.readInt()
            t.setUsers((0 until count).map { p.readUTF() })
        }
    }

    fun onContainerUpdate(p: PacketParser) {
        val windowId = p.readUnsignedByte()
        if (p.player.openContainer != null && p.player.openContainer.windowId == windowId) {
            when (val container = p.player.openContainer) {
                is container.Player -> container.updateCustomData(p.readNBT())
                else -> {} // Invalid packet.
            }
        }
    }

    fun onDisassemblerActiveChange(p: PacketParser) {
        val t = p.readTileEntity<Disassembler>()
        if (t != null) {
            t.isActive = p.readBoolean()
        }
    }

    fun onFileSystemActivity(p: PacketParser) {
        val sound = p.readUTF()
        val data = CompressedStreamTools.read(p)
        if (p.readBoolean()) {
            val t = p.readTileEntity<net.minecraft.tileentity.TileEntity>()
            if (t != null) {
                MinecraftForge.EVENT_BUS.post(FileSystemAccessEvent.Client(sound, t, data))
            }
        } else {
            val world = world(p.player, p.readInt())
            if (world != null) {
                val x = p.readDouble()
                val y = p.readDouble()
                val z = p.readDouble()
                MinecraftForge.EVENT_BUS.post(FileSystemAccessEvent.Client(sound, world, x, y, z, data))
            }
        }
    }

    fun onNetworkActivity(p: PacketParser) {
        val data = CompressedStreamTools.read(p)
        if (p.readBoolean()) {
            val t = p.readTileEntity<net.minecraft.tileentity.TileEntity>()
            if (t != null) {
                MinecraftForge.EVENT_BUS.post(NetworkActivityEvent.Client(t, data))
            }
        } else {
            val world = world(p.player, p.readInt())
            if (world != null) {
                val x = p.readDouble()
                val y = p.readDouble()
                val z = p.readDouble()
                MinecraftForge.EVENT_BUS.post(NetworkActivityEvent.Client(world, x, y, z, data))
            }
        }
    }

    fun onFloppyChange(p: PacketParser) {
        val t = p.readTileEntity<DiskDrive>()
        if (t != null) {
            t.setInventorySlotContents(0, p.readItemStack())
        }
    }

    fun onHologramClear(p: PacketParser) {
        val t = p.readTileEntity<Hologram>()
        if (t != null) {
            for (i in t.volume.indices) {
                t.volume[i] = 0
            }
            t.needsRendering = true
        }
    }

    fun onHologramColor(p: PacketParser) {
        val t = p.readTileEntity<Hologram>()
        if (t != null) {
            val index = p.readInt()
            val value = p.readInt()
            t.colors[index] = value and 0xFFFFFF
            t.needsRendering = true
        }
    }

    fun onHologramPowerChange(p: PacketParser) {
        val t = p.readTileEntity<Hologram>()
        if (t != null) {
            t.hasPower = p.readBoolean()
        }
    }

    fun onHologramScale(p: PacketParser) {
        val t = p.readTileEntity<Hologram>()
        if (t != null) {
            t.scale = p.readDouble()
        }
    }

    fun onHologramArea(p: PacketParser) {
        val t = p.readTileEntity<Hologram>()
        if (t != null) {
            val fromX = p.readByte().toInt()
            val untilX = p.readByte().toInt()
            val fromZ = p.readByte().toInt()
            val untilZ = p.readByte().toInt()
            for (x in fromX until untilX) {
                for (z in fromZ until untilZ) {
                    t.volume[x + z * t.width] = p.readInt()
                    t.volume[x + z * t.width + t.width * t.width] = p.readInt()
                }
            }
            t.needsRendering = true
        }
    }

    fun onHologramValues(p: PacketParser) {
        val t = p.readTileEntity<Hologram>()
        if (t != null) {
            val count = p.readInt()
            for (i in 0 until count) {
                val xz = p.readShort()
                val x = (xz.toInt() shr 8).toByte()
                val z = xz.toByte()
                t.volume[x + z * t.width] = p.readInt()
                t.volume[x + z * t.width + t.width * t.width] = p.readInt()
            }
            t.needsRendering = true
        }
    }

    fun onHologramPositionOffsetY(p: PacketParser) {
        val t = p.readTileEntity<Hologram>()
        if (t != null) {
            val x = p.readDouble()
            val y = p.readDouble()
            val z = p.readDouble()
            t.translation = Vec3d(x, y, z)
        }
    }

    fun onHologramRotation(p: PacketParser) {
        val t = p.readTileEntity<Hologram>()
        if (t != null) {
            t.rotationAngle = p.readFloat()
            t.rotationX = p.readFloat()
            t.rotationY = p.readFloat()
            t.rotationZ = p.readFloat()
        }
    }

    fun onHologramRotationSpeed(p: PacketParser) {
        val t = p.readTileEntity<Hologram>()
        if (t != null) {
            t.rotationSpeed = p.readFloat()
            t.rotationSpeedX = p.readFloat()
            t.rotationSpeedY = p.readFloat()
            t.rotationSpeedZ = p.readFloat()
        }
    }

    fun onLootDisk(p: PacketParser) {
        val stack = p.readItemStack()
        if (!stack.isEmpty) {
            Loot.disksForClient += stack
        }
        if (Mods.JustEnoughItems.isModAvailable) {
            addDiskToJEI(stack)
        }
    }

    @Optional.Method(modid = Mods.IDs.JustEnoughItems)
    private fun addDiskToJEI(stack: ItemStack) {
        ModJEI.addDiskAtRuntime(stack)
    }

    fun onCyclingDisk(p: PacketParser) {
        val stack = p.readItemStack()
        if (!stack.isEmpty) {
            Loot.disksForCyclingClient += stack
        }
    }

    fun onNanomachinesConfiguration(p: PacketParser) {
        val player = p.readEntity<EntityPlayer>()
        if (player != null) {
            val hasController = p.readBoolean()
            if (hasController) {
                when (val controller = api.Nanomachines.installController(player)) {
                    is ControllerImpl -> controller.load(p.readNBT())
                    else -> {} // Wat.
                }
            } else {
                api.Nanomachines.uninstallController(player)
            }
        }
    }

    fun onNanomachinesInputs(p: PacketParser) {
        val player = p.readEntity<EntityPlayer>()
        if (player != null) {
            when (val controller = api.Nanomachines.getController(player)) {
                is ControllerImpl -> {
                    val inputs = ByteArray(p.readInt())
                    p.read(inputs)
                    controller.configuration.synchronized {
                        for ((index, value) in inputs.withIndex()) {
                            if (index < controller.configuration.triggers.size) {
                                controller.configuration.triggers[index].isActive = value == 1.toByte()
                            }
                        }
                        controller.activeBehaviorsDirty = true
                    }
                }
                else -> {} // Wat.
            }
        }
    }

    fun onNanomachinesPower(p: PacketParser) {
        val player = p.readEntity<EntityPlayer>()
        if (player != null) {
            when (val controller = api.Nanomachines.getController(player)) {
                is ControllerImpl -> controller.storedEnergy = p.readDouble()
                else -> {} // Wat.
            }
        }
    }

    fun onNetSplitterState(p: PacketParser) {
        val t = p.readTileEntity<NetSplitter>()
        if (t != null) {
            t.isInverted = p.readBoolean()
            t.openSides = t.uncompressSides(p.readByte())
            t.world.notifyBlockUpdate(t.pos)
        }
    }

    fun onParticleEffect(p: PacketParser) {
        val dimension = p.readInt()
        val world = world(p.player, dimension)
        if (world != null) {
            val x = p.readInt()
            val y = p.readInt()
            val z = p.readInt()
            val velocity = p.readDouble()
            val direction = p.readDirection()
            val particleType = EnumParticleTypes.getParticleFromId(p.readInt())
            val count = p.readUnsignedByte() / (1 shl Minecraft.getMinecraft().gameSettings.particleSetting)

            for (i in 0 until count) {
                fun rv(f: (EnumFacing) -> Int): Double {
                    return if (direction != null) {
                        world.rand.nextFloat() - 0.5 + f(direction) * 0.5
                    } else {
                        world.rand.nextFloat() * 2.0 - 1
                    }
                }

                val vx = rv { it.xOffset }
                val vy = rv { it.yOffset }
                val vz = rv { it.zOffset }
                if (vx * vx + vy * vy + vz * vz < 1) {
                    fun rp(x: Int, v: Double, f: (EnumFacing) -> Int): Double {
                        return if (direction != null) {
                            x + 0.5 + v * velocity * 0.5 + f(direction) * velocity
                        } else {
                            x + 0.5 + v * velocity
                        }
                    }

                    val px = rp(x, vx) { it.xOffset }
                    val py = rp(y, vy) { it.yOffset }
                    val pz = rp(z, vz) { it.zOffset }
                    world.spawnParticle(particleType, px, py, pz, vx, vy + velocity * 0.25, vz)
                }
            }
        }
    }

    fun onPetVisibility(p: PacketParser) {
        if (!PetRenderer.isInitialized) {
            PetRenderer.isInitialized = true
            if (Settings.get().hideOwnPet) {
                PetRenderer.hidden += Minecraft.getMinecraft().player.name
            }
            PacketSender.sendPetVisibility()
        }

        val count = p.readInt()
        for (i in 0 until count) {
            val name = p.readUTF()
            if (p.readBoolean()) {
                PetRenderer.hidden -= name
            } else {
                PetRenderer.hidden += name
            }
        }
    }

    fun onPowerState(p: PacketParser) {
        val t = p.readTileEntity<PowerInformation>()
        if (t != null) {
            t.globalBuffer = p.readDouble()
            t.globalBufferSize = p.readDouble()
        }
    }

    fun onPrinterState(p: PacketParser) {
        val t = p.readTileEntity<Printer>()
        if (t != null) {
            if (p.readBoolean()) {
                t.requiredEnergy = 9001.0
            } else {
                t.requiredEnergy = 0.0
            }
        }
    }

    fun onRackInventory(p: PacketParser) {
        val t = p.readTileEntity<Rack>()
        if (t != null) {
            val count = p.readInt()
            for (i in 0 until count) {
                val slot = p.readInt()
                t.setInventorySlotContents(slot, p.readItemStack())
            }
        }
    }

    fun onRackMountableData(p: PacketParser) {
        val t = p.readTileEntity<Rack>()
        if (t != null) {
            val mountableIndex = p.readInt()
            t.lastData[mountableIndex] = p.readNBT()
            t.world.notifyBlockUpdate(t.pos)
        }
    }

    fun onRaidStateChange(p: PacketParser) {
        val t = p.readTileEntity<Raid>()
        if (t != null) {
            for (slot in 0 until t.sizeInventory) {
                t.presence[slot] = p.readBoolean()
            }
        }
    }

    fun onRedstoneState(p: PacketParser) {
        val t = p.readTileEntity<RedstoneAware>()
        if (t != null) {
            t.setOutputEnabled(p.readBoolean())
            for (d in EnumFacing.values()) {
                t.setOutput(d, p.readByte())
            }
        }
    }

    fun onRobotAnimateSwing(p: PacketParser) {
        val t = p.readTileEntity<RobotProxy>()
        if (t != null) {
            t.robot.setAnimateSwing(p.readInt())
        }
    }

    fun onRobotAnimateTurn(p: PacketParser) {
        val t = p.readTileEntity<RobotProxy>()
        if (t != null) {
            t.robot.setAnimateTurn(p.readByte(), p.readInt())
        }
    }

    fun onRobotAssemblingState(p: PacketParser) {
        val t = p.readTileEntity<Assembler>()
        if (t != null) {
            if (p.readBoolean()) {
                t.requiredEnergy = 9001.0
            } else {
                t.requiredEnergy = 0.0
            }
        }
    }

    fun onRobotInventoryChange(p: PacketParser) {
        val t = p.readTileEntity<RobotProxy>()
        if (t != null) {
            val robot = t.robot
            val slot = p.readInt()
            val stack = p.readItemStack()
            if (slot >= robot.sizeInventory - robot.componentCount) {
                robot.info.components[slot - (robot.sizeInventory - robot.componentCount)] = stack
            } else {
                t.robot.setInventorySlotContents(slot, stack)
            }
        }
    }

    fun onRobotLightChange(p: PacketParser) {
        val t = p.readTileEntity<RobotProxy>()
        if (t != null) {
            t.robot.info.lightColor = p.readInt()
        }
    }

    fun onRobotNameChange(p: PacketParser) {
        val t = p.readTileEntity<RobotProxy>()
        if (t != null) {
            val len = p.readShort().toInt()
            val name = CharArray(len)
            for (x in 0 until len) {
                name[x] = p.readChar()
            }
            t.robot.setName(name.concatToString())
        }
    }

    fun onRobotMove(p: PacketParser) {
        val dimension = p.readInt()
        val x = p.readInt()
        val y = p.readInt()
        val z = p.readInt()
        val direction = p.readDirection()
        val t = p.getTileEntity<RobotProxy>(dimension, x, y, z)
        when {
            t != null && direction != null -> t.robot.move(direction)
            direction != null -> {
                // Invalid packet, robot may be coming from outside our loaded area.
                PacketSender.sendRobotStateRequest(dimension, x + direction.xOffset, y + direction.yOffset, z + direction.zOffset)
            }
            else -> {} // Invalid packet.
        }
    }

    fun onRobotSelectedSlotChange(p: PacketParser) {
        val t = p.readTileEntity<RobotProxy>()
        if (t != null) {
            t.robot.selectedSlot = p.readInt()
        }
    }

    fun onRotatableState(p: PacketParser) {
        val t = p.readTileEntity<Rotatable>()
        if (t != null) {
            t.pitch = p.readDirection()!!
            t.yaw = p.readDirection()!!
        }
    }

    fun onSwitchActivity(p: PacketParser) {
        val t = p.readTileEntity<Relay>()
        if (t != null) {
            t.lastMessage = System.currentTimeMillis()
        }
    }

    fun onTextBufferPowerChange(p: PacketParser) {
        val buffer = ComponentTracker.get(p.player.entityWorld, p.readUTF())
        if (buffer is api.internal.TextBuffer) {
            buffer.setRenderingEnabled(p.readBoolean())
        }
    }

    fun onTextBufferInit(p: PacketParser) {
        val buffer = ComponentTracker.get(p.player.entityWorld, p.readUTF())
        if (buffer is li.cil.oc.common.component.TextBuffer) {
            val nbt = p.readNBT()
            if (nbt.hasKey("maxWidth")) {
                val maxWidth = nbt.getInteger("maxWidth")
                val maxHeight = nbt.getInteger("maxHeight")
                buffer.setMaximumResolution(maxWidth, maxHeight)
            }
            buffer.data.load(nbt)
            if (nbt.hasKey("viewportWidth")) {
                val viewportWidth = nbt.getInteger("viewportWidth")
                val viewportHeight = nbt.getInteger("viewportHeight")
                buffer.setViewport(viewportWidth, viewportHeight)
            }
            buffer.proxy.markDirty()
            buffer.markInitialized()
        }
    }

    fun onTextBufferMulti(p: PacketParser) {
        if (p.player != null) {
            val buffer = ComponentTracker.get(p.player.entityWorld, p.readUTF())
            if (buffer is api.internal.TextBuffer) {
                try {
                    while (true) {
                        when (p.readPacketType()) {
                            PacketType.TextBufferMultiColorChange -> onTextBufferMultiColorChange(p, buffer)
                            PacketType.TextBufferMultiCopy -> onTextBufferMultiCopy(p, buffer)
                            PacketType.TextBufferMultiDepthChange -> onTextBufferMultiDepthChange(p, buffer)
                            PacketType.TextBufferMultiFill -> onTextBufferMultiFill(p, buffer)
                            PacketType.TextBufferMultiPaletteChange -> onTextBufferMultiPaletteChange(p, buffer)
                            PacketType.TextBufferMultiResolutionChange -> onTextBufferMultiResolutionChange(p, buffer)
                            PacketType.TextBufferMultiViewportResolutionChange -> onTextBufferMultiViewportResolutionChange(p, buffer)
                            PacketType.TextBufferMultiMaxResolutionChange -> onTextBufferMultiMaxResolutionChange(p, buffer)
                            PacketType.TextBufferMultiSet -> onTextBufferMultiSet(p, buffer)
                            PacketType.TextBufferRamInit -> onTextBufferRamInit(p, buffer)
                            PacketType.TextBufferBitBlt -> onTextBufferBitBlt(p, buffer)
                            PacketType.TextBufferRamDestroy -> onTextBufferRamDestroy(p, buffer)
                            PacketType.TextBufferMultiRawSetText -> onTextBufferMultiRawSetText(p, buffer)
                            PacketType.TextBufferMultiRawSetBackground -> onTextBufferMultiRawSetBackground(p, buffer)
                            PacketType.TextBufferMultiRawSetForeground -> onTextBufferMultiRawSetForeground(p, buffer)
                            else -> {} // Invalid packet.
                        }
                    }
                } catch (ignored: EOFException) {
                    // No more commands.
                }
            }
        }
    }

    fun onTextBufferMultiColorChange(p: PacketParser, env: api.internal.TextBuffer) {
        if (env is api.internal.TextBuffer) {
            val foreground = p.readInt()
            val foregroundIsPalette = p.readBoolean()
            env.setForegroundColor(foreground, foregroundIsPalette)
            val background = p.readInt()
            val backgroundIsPalette = p.readBoolean()
            env.setBackgroundColor(background, backgroundIsPalette)
        }
    }

    fun onTextBufferMultiCopy(p: PacketParser, buffer: api.internal.TextBuffer) {
        val col = p.readInt()
        val row = p.readInt()
        val w = p.readInt()
        val h = p.readInt()
        val tx = p.readInt()
        val ty = p.readInt()
        buffer.copy(col, row, w, h, tx, ty)
    }

    fun onTextBufferMultiDepthChange(p: PacketParser, buffer: api.internal.TextBuffer) {
        buffer.setColorDepth(api.internal.TextBuffer.ColorDepth.values()[p.readInt()])
    }

    fun onTextBufferMultiFill(p: PacketParser, buffer: api.internal.TextBuffer) {
        val col = p.readInt()
        val row = p.readInt()
        val w = p.readInt()
        val h = p.readInt()
        val c = p.readMedium()
        buffer.fill(col, row, w, h, c)
    }

    fun onTextBufferMultiPaletteChange(p: PacketParser, buffer: api.internal.TextBuffer) {
        val index = p.readInt()
        val color = p.readInt()
        buffer.setPaletteColor(index, color)
    }

    fun onTextBufferMultiResolutionChange(p: PacketParser, buffer: api.internal.TextBuffer) {
        val w = p.readInt()
        val h = p.readInt()
        buffer.setResolution(w, h)
    }

    fun onTextBufferMultiViewportResolutionChange(p: PacketParser, buffer: api.internal.TextBuffer) {
        val w = p.readInt()
        val h = p.readInt()
        buffer.setViewport(w, h)
    }

    fun onTextBufferMultiMaxResolutionChange(p: PacketParser, buffer: api.internal.TextBuffer) {
        val w = p.readInt()
        val h = p.readInt()
        buffer.setMaximumResolution(w, h)
    }

    fun onTextBufferMultiSet(p: PacketParser, buffer: api.internal.TextBuffer) {
        val col = p.readInt()
        val row = p.readInt()
        val s = p.readUTF()
        val vertical = p.readBoolean()
        buffer.set(col, row, s, vertical)
    }

    fun onTextBufferRamInit(p: PacketParser, buffer: api.internal.TextBuffer) {
        val owner = p.readUTF()
        val id = p.readInt()
        val nbt = p.readNBT()

        component.ClientGpuTextBufferHandler.loadBuffer(buffer, owner, id, nbt)
    }

    fun onTextBufferBitBlt(p: PacketParser, buffer: api.internal.TextBuffer) {
        val col = p.readInt()
        val row = p.readInt()
        val w = p.readInt()
        val h = p.readInt()
        val owner = p.readUTF()
        val id = p.readInt()
        val fromCol = p.readInt()
        val fromRow = p.readInt()

        component.ClientGpuTextBufferHandler.bitblt(buffer, col, row, w, h, owner, id, fromCol, fromRow)
    }

    fun onTextBufferRamDestroy(p: PacketParser, buffer: api.internal.TextBuffer) {
        val owner = p.readUTF()
        val id = p.readInt()

        component.ClientGpuTextBufferHandler.removeBuffer(buffer, owner, id)
    }

    fun onTextBufferMultiRawSetText(p: PacketParser, buffer: api.internal.TextBuffer) {
        val col = p.readInt()
        val row = p.readInt()

        val rows = p.readShort().toInt()
        val text = Array(rows) { IntArray(0) }
        for (y in 0 until rows) {
            val cols = p.readShort().toInt()
            val line = IntArray(cols)
            for (x in 0 until cols) {
                line[x] = p.readMedium()
            }
            text[y] = line
        }

        buffer.rawSetText(col, row, text)
    }

    fun onTextBufferMultiRawSetBackground(p: PacketParser, buffer: api.internal.TextBuffer) {
        val col = p.readInt()
        val row = p.readInt()

        val rows = p.readShort().toInt()
        val color = Array(rows) { IntArray(0) }
        for (y in 0 until rows) {
            val cols = p.readShort().toInt()
            val line = IntArray(cols)
            for (x in 0 until cols) {
                line[x] = p.readInt()
            }
            color[y] = line
        }

        buffer.rawSetBackground(col, row, color)
    }

    fun onTextBufferMultiRawSetForeground(p: PacketParser, buffer: api.internal.TextBuffer) {
        val col = p.readInt()
        val row = p.readInt()

        val rows = p.readShort().toInt()
        val color = Array(rows) { IntArray(0) }
        for (y in 0 until rows) {
            val cols = p.readShort().toInt()
            val line = IntArray(cols)
            for (x in 0 until cols) {
                line[x] = p.readInt()
            }
            color[y] = line
        }

        buffer.rawSetForeground(col, row, color)
    }

    fun onScreenTouchMode(p: PacketParser) {
        val t = p.readTileEntity<Screen>()
        if (t != null) {
            t.invertTouchMode = p.readBoolean()
        }
    }

    fun onSoundEffect(p: PacketParser) {
        val dimension = p.readInt()
        val world = world(p.player, dimension)
        if (world != null) {
            val x = p.readDouble()
            val y = p.readDouble()
            val z = p.readDouble()
            val sound = p.readUTF()
            val category = SoundCategory.values()[p.readByte().toInt()]
            val range = p.readFloat()
            world.playSound(p.player, x, y, z, SoundEvent(ResourceLocation(sound)), category, range / 15 + 0.5F, 1.0F)
        }
    }

    fun onSound(p: PacketParser) {
        val dimension = p.readInt()
        if (world(p.player, dimension) != null) {
            val x = p.readInt()
            val y = p.readInt()
            val z = p.readInt()
            val frequency = p.readShort()
            val duration = p.readShort()
            Audio.play(x + 0.5f, y + 0.5f, z + 0.5f, frequency, duration)
        }
    }

    fun onSoundPattern(p: PacketParser) {
        val dimension = p.readInt()
        if (world(p.player, dimension) != null) {
            val x = p.readInt()
            val y = p.readInt()
            val z = p.readInt()
            val pattern = p.readUTF()
            Audio.play(x + 0.5f, y + 0.5f, z + 0.5f, pattern)
        }
    }

    fun onTransposerActivity(p: PacketParser) {
        val transposer = p.readTileEntity<Transposer>()
        if (transposer != null) {
            transposer.lastOperation = System.currentTimeMillis()
        }
    }

    fun onWaypointLabel(p: PacketParser) {
        val waypoint = p.readTileEntity<Waypoint>()
        if (waypoint != null) {
            waypoint.label = p.readUTF()
        }
    }
}
