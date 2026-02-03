package li.cil.oc.server

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import li.cil.oc.Settings
import li.cil.oc.api.*
import li.cil.oc.api.event.FileSystemAccessEvent
import li.cil.oc.api.event.NetworkActivityEvent
import li.cil.oc.api.internal.TextBuffer
import li.cil.oc.api.network.EnvironmentHost
import li.cil.oc.api.network.Node
import li.cil.oc.common.*
import li.cil.oc.common.nanomachines.ControllerImpl
import li.cil.oc.common.tileentity.Waypoint
import li.cil.oc.common.tileentity.traits.*
import li.cil.oc.util.BlockPosition
import li.cil.oc.util.PackedColor
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.inventory.Container
import net.minecraft.item.ItemStack
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.common.MinecraftForge
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.round
import li.cil.oc.api.Nanomachines as ApiNanomachines
import li.cil.oc.common.tileentity.Adapter as TEAdapter
import li.cil.oc.common.tileentity.Assembler as TEAssembler
import li.cil.oc.common.tileentity.Charger as TECharger
import li.cil.oc.common.tileentity.Disassembler as TEDisassembler
import li.cil.oc.common.tileentity.DiskDrive as TEDiskDrive
import li.cil.oc.common.tileentity.Hologram as TEHologram
import li.cil.oc.common.tileentity.NetSplitter as TENetSplitter
import li.cil.oc.common.tileentity.Printer as TEPrinter
import li.cil.oc.common.tileentity.Rack as TERack
import li.cil.oc.common.tileentity.Raid as TERaid
import li.cil.oc.common.tileentity.Relay as TERelay
import li.cil.oc.common.tileentity.Robot as TERobot
import li.cil.oc.common.tileentity.Screen as TEScreen
import li.cil.oc.common.tileentity.Transposer as TETransposer
import li.cil.oc.common.tileentity.traits.Computer as TEComputer


object PacketSender {
  private fun SimplePacketBuilder.writeTileEntity(t: TileEntityTrait)
    = this.writeTileEntity(t.asTileEntity())
  private fun SimplePacketBuilder.sendToPlayersNearTileEntity(t: TileEntityTrait)
    = this.sendToPlayersNearTileEntity(t.asTileEntity())

  fun sendAdapterState(t: TEAdapter) {
    val pb = SimplePacketBuilder(PacketType.AdapterState)

    pb.writeTileEntity(t)
    pb.writeByte(t.compressSides().toUByte().toUInt().toInt())

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendAnalyze(address: String, player: EntityPlayerMP) {
    val pb = SimplePacketBuilder(PacketType.Analyze)

    pb.writeUTF(address)

    pb.sendToPlayer(player)
  }

  fun sendChargerState(t: TECharger) {
    val pb = SimplePacketBuilder(PacketType.ChargerState)

    pb.writeTileEntity(t)
    pb.writeDouble(t.chargeSpeed)
    pb.writeBoolean(t.hasPower)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendClientLog(line: String, player: EntityPlayerMP) {
    val pb = CompressedPacketBuilder(PacketType.ClientLog)

    pb.writeUTF(line)

    pb.sendToPlayer(player)
  }

  fun sendClipboard(player: EntityPlayerMP, text: String) {
    val pb = SimplePacketBuilder(PacketType.Clipboard)

    pb.writeUTF(text)

    pb.sendToPlayer(player)
  }

  fun sendColorChange(t: Colored) {
    val pb = SimplePacketBuilder(PacketType.ColorChange)

    pb.writeTileEntity(t)
    pb.writeInt(t.color.toInt())

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendComputerState(t: TEComputer) {
    val pb = SimplePacketBuilder(PacketType.ComputerState)

    pb.writeTileEntity(t)
    pb.writeBoolean(t.isRunning)
    pb.writeBoolean(t.hasErrored)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendMachineItemState(player: EntityPlayerMP, stack: ItemStack, isRunning: Boolean) {
    val pb = SimplePacketBuilder(PacketType.MachineItemStateResponse)

    pb.writeItemStack(stack)
    pb.writeBoolean(isRunning)

    pb.sendToPlayer(player)
  }

  fun sendComputerUserList(t: TEComputer, list: Array<out String>) {
    val pb = SimplePacketBuilder(PacketType.ComputerUserList)

    pb.writeTileEntity(t)
    pb.writeInt(list.size)
    list.forEach(pb::writeUTF)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendContainerUpdate(c: Container, nbt: NBTTagCompound, player: EntityPlayerMP) {
    if (!nbt.isEmpty) {
      val pb = SimplePacketBuilder(PacketType.ContainerUpdate)

      pb.writeByte(c.windowId.toUByte().toUInt().toInt())
      pb.writeNBT(nbt)

      pb.sendToPlayer(player)
    }
  }

  fun sendDisassemblerActive(t: TEDisassembler, active: Boolean) {
    val pb = SimplePacketBuilder(PacketType.DisassemblerActiveChange)

    pb.writeTileEntity(t)
    pb.writeBoolean(active)

    pb.sendToPlayersNearTileEntity(t)
  }

  // Avoid spamming the network with disk activity notices.
  private val fileSystemAccessTimeouts = WeakHashMap<Node, Cache<String, Long>>()

  fun sendFileSystemActivity(node: Node, host: EnvironmentHost, name: String) {
    val diskActivityPacketDelay = Settings.get.diskActivitySoundDelay

    if (diskActivityPacketDelay >= 0) {
      val hostTimeouts = synchronized(fileSystemAccessTimeouts) {
        fileSystemAccessTimeouts.getOrPut(node) {
          CacheBuilder.newBuilder().concurrencyLevel(Settings.get.threads).maximumSize(250)
            .expireAfterWrite(diskActivityPacketDelay.toLong(), TimeUnit.MILLISECONDS)
            .build()
        }
      }
      val lastHostTimeout = hostTimeouts.getIfPresent(name)
      if (lastHostTimeout == null || lastHostTimeout <= System.currentTimeMillis()) {
        val event = when (host) {
          is net.minecraft.tileentity.TileEntity -> FileSystemAccessEvent.Server(name, host, node)
          else -> FileSystemAccessEvent.Server(name, host.world(), host.xPosition(), host.yPosition(), host.zPosition(), node)
        }
        MinecraftForge.EVENT_BUS.post(event)
        if (!event.isCanceled) {
          hostTimeouts.put(name, System.currentTimeMillis() + diskActivityPacketDelay)

          val pb = SimplePacketBuilder(PacketType.FileSystemActivity)

          pb.writeUTF(event.sound)
          CompressedStreamTools.write(event.data, pb)
          when (val te = event.tileEntity) {
            is net.minecraft.tileentity.TileEntity -> {
              pb.writeBoolean(true)
              pb.writeTileEntity(te)
            }
            else -> {
              pb.writeBoolean(false)
              pb.writeInt(event.world.provider.dimension)
              pb.writeDouble(event.x)
              pb.writeDouble(event.y)
              pb.writeDouble(event.z)
            }
          }

          pb.sendToPlayersNearHost(host, Settings.get.maxNetworkClientSoundPacketDistance)
        }
      }
    }
  }

  fun sendNetworkActivity(node: Node, host: EnvironmentHost) {
    val event = when (host) {
      is net.minecraft.tileentity.TileEntity -> NetworkActivityEvent.Server(host, node)
      else -> NetworkActivityEvent.Server(host.world(), host.xPosition(), host.yPosition(), host.zPosition(), node)
    }
    MinecraftForge.EVENT_BUS.post(event)
    if (!event.isCanceled) {

      val pb = SimplePacketBuilder(PacketType.NetworkActivity)

      CompressedStreamTools.write(event.data, pb)
      when (val te = event.tileEntity) {
        is net.minecraft.tileentity.TileEntity -> {
          pb.writeBoolean(true)
          pb.writeTileEntity(te)
        }

        else -> {
          pb.writeBoolean(false)
          pb.writeInt(event.world.provider.dimension)
          pb.writeDouble(event.x)
          pb.writeDouble(event.y)
          pb.writeDouble(event.z)
        }
      }

      pb.sendToPlayersNearHost(host, Settings.get.maxNetworkClientEffectPacketDistance)
    }
  }

  fun sendFloppyChange(t: TEDiskDrive, stack: ItemStack = ItemStack.EMPTY) {
    val pb = SimplePacketBuilder(PacketType.FloppyChange)

    pb.writeTileEntity(t)
    pb.writeItemStack(stack)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendHologramClear(t: TEHologram) {
    val pb = SimplePacketBuilder(PacketType.HologramClear)

    pb.writeTileEntity(t)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendHologramColor(t: TEHologram, index: Int, value: Int) {
    val pb = SimplePacketBuilder(PacketType.HologramColor)

    pb.writeTileEntity(t)
    pb.writeInt(index)
    pb.writeInt(value)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendHologramPowerChange(t: TEHologram) {
    val pb = SimplePacketBuilder(PacketType.HologramPowerChange)

    pb.writeTileEntity(t)
    pb.writeBoolean(t.hasPower)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendHologramScale(t: TEHologram) {
    val pb = SimplePacketBuilder(PacketType.HologramScale)

    pb.writeTileEntity(t)
    pb.writeDouble(t.scale)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendHologramArea(t: TEHologram) {
    val pb = CompressedPacketBuilder(PacketType.HologramArea)

    pb.writeTileEntity(t)
    pb.writeByte(t.dirtyFromX)
    pb.writeByte(t.dirtyUntilX)
    pb.writeByte(t.dirtyFromZ)
    pb.writeByte(t.dirtyUntilZ)
    for (x in t.dirtyFromX until t.dirtyUntilX) {
      for (z in t.dirtyFromZ until t.dirtyUntilZ) {
        pb.writeInt(t.volume[x + z * t.width])
        pb.writeInt(t.volume[x + z * t.width + t.width * t.width])
      }
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendHologramValues(t: TEHologram) {
    val pb = CompressedPacketBuilder(PacketType.HologramValues)

    pb.writeTileEntity(t)
    pb.writeInt(t.dirty.size)
    for (xz in t.dirty) {
      val x = (xz.toInt() shr 8).toByte()
      val z = xz.toByte()
      pb.writeShort(xz.toUShort().toUInt().toInt())
      val rangeStart: Int = x + z * t.width
      val rangeFinal: Int = x + z * t.width + t.width * t.width
      pb.writeInt(t.volume[rangeStart.coerceIn(t.volume.indices)])
      pb.writeInt(t.volume[rangeFinal.coerceIn(t.volume.indices)])
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendHologramOffset(t: TEHologram) {
    val pb = SimplePacketBuilder(PacketType.HologramTranslation)

    pb.writeTileEntity(t)
    pb.writeDouble(t.translation.x)
    pb.writeDouble(t.translation.y)
    pb.writeDouble(t.translation.z)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendHologramRotation(t: TEHologram) {
    val pb = SimplePacketBuilder(PacketType.HologramRotation)

    pb.writeTileEntity(t)
    pb.writeFloat(t.rotationAngle)
    pb.writeFloat(t.rotationX)
    pb.writeFloat(t.rotationY)
    pb.writeFloat(t.rotationZ)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendHologramRotationSpeed(t: TEHologram) {
    val pb = SimplePacketBuilder(PacketType.HologramRotationSpeed)

    pb.writeTileEntity(t)
    pb.writeFloat(t.rotationSpeed)
    pb.writeFloat(t.rotationSpeedX)
    pb.writeFloat(t.rotationSpeedY)
    pb.writeFloat(t.rotationSpeedZ)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendLootDisks(p: EntityPlayerMP) {
    // Sending as separate packets, because CompressedStreamTools hiccups otherwise...
    val stacks = Loot.worldDisks.map { it.first }
    for (stack in stacks) {
      val pb = SimplePacketBuilder(PacketType.LootDisk)

      pb.writeItemStack(stack)

      pb.sendToPlayer(p)
    }
    for (stack in Loot.disksForCyclingServer) {
      val pb = SimplePacketBuilder(PacketType.CyclingDisk)

      pb.writeItemStack(stack)

      pb.sendToPlayer(p)
    }
  }

  fun sendNanomachineConfiguration(player: EntityPlayer) {
    val pb = SimplePacketBuilder(PacketType.NanomachinesConfiguration)

    pb.writeEntity(player)
    when (val controller = ApiNanomachines.getController(player)) {
      is ControllerImpl -> {
        pb.writeBoolean(true)
        val nbt = NBTTagCompound()
        controller.save(nbt)
        pb.writeNBT(nbt)
      }
      else -> {
        pb.writeBoolean(false)
      }
    }

    pb.sendToPlayersNearEntity(player)
  }

  fun sendNanomachineInputs(player: EntityPlayer) {
    val controller = ApiNanomachines.getController(player) as? ControllerImpl ?: return // Wat.
    val pb = SimplePacketBuilder(PacketType.NanomachinesInputs)

    pb.writeEntity(player)
    val inputs = controller.configuration.triggers.map { if (it.isActive) 1.toByte() else 0.toByte() }.toByteArray()
    pb.writeInt(inputs.size)
    pb.write(inputs)

    pb.sendToPlayersNearEntity(player)
  }

  fun sendNanomachinePower(player: EntityPlayer) {
    val controller = ApiNanomachines.getController(player) as? ControllerImpl ?: return // Wat.
    val pb = SimplePacketBuilder(PacketType.NanomachinesPower)

    pb.writeEntity(player)
    pb.writeDouble(controller.localBuffer)

    pb.sendToPlayersNearEntity(player)
  }

  fun sendNetSplitterState(t: TENetSplitter) {
    val pb = SimplePacketBuilder(PacketType.NetSplitterState)

    pb.writeTileEntity(t)
    pb.writeBoolean(t.isInverted)
    pb.writeByte(t.compressSides().toInt())

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendParticleEffect(
    position: BlockPosition,
    particleType: EnumParticleTypes,
    count: Int,
    velocity: Double,
    direction: EnumFacing? = null
  ): Unit {
    if (count > 0) {
      val pb = SimplePacketBuilder(PacketType.ParticleEffect)

      pb.writeInt(position.world!!.provider.dimension)
      pb.writeInt(position.x)
      pb.writeInt(position.y)
      pb.writeInt(position.z)
      pb.writeDouble(velocity)
      pb.writeDirection(direction)
      pb.writeInt(particleType.particleID)
      pb.writeByte(count.toByte().toInt())

      pb.sendToNearbyPlayers(
        position.world,
        position.x.toDouble(),
        position.y.toDouble(),
        position.z.toDouble(),
        Settings.get.maxNetworkClientEffectPacketDistance / 2.0
      )
    }
  }

  fun sendPetVisibility(name: String? = null, player: EntityPlayerMP? = null) {
    val pb = SimplePacketBuilder(PacketType.PetVisibility)

    if (name != null) {
      pb.writeInt(1)
      pb.writeUTF(name)
      pb.writeBoolean(name !in PetVisibility.hidden)
    } else {
      pb.writeInt(PetVisibility.hidden.size)
      for (n in PetVisibility.hidden) {
        pb.writeUTF(n)
        pb.writeBoolean(false)
      }
    }

    if (player == null)
      pb.sendToAllPlayers()
    else
      pb.sendToPlayer(player)
  }

  fun sendPowerState(t: PowerInformation) {
    val pb = SimplePacketBuilder(PacketType.PowerState)

    pb.writeTileEntity(t)
    pb.writeDouble(round(t.globalBuffer))
    pb.writeDouble(t.globalBufferSize)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendPrinting(t: TEPrinter, printing: Boolean) {
    val pb = SimplePacketBuilder(PacketType.PrinterState)

    pb.writeTileEntity(t)
    pb.writeBoolean(printing)

    pb.sendToPlayersNearHost(t)
  }

  fun sendRackInventory(t: TERack) {
    val pb = SimplePacketBuilder(PacketType.RackInventory)

    pb.writeTileEntity(t)
    pb.writeInt(t.sizeInventory)
    for (slot in 0 until t.sizeInventory) {
      pb.writeInt(slot)
      pb.writeItemStack(t.getStackInSlot(slot))
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendRackInventory(t: TERack, slot: Int) {
    val pb = SimplePacketBuilder(PacketType.RackInventory)

    pb.writeTileEntity(t)
    pb.writeInt(1)
    pb.writeInt(slot)
    pb.writeItemStack(t.getStackInSlot(slot))

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendRackMountableData(t: TERack, mountable: Int) {
    val pb = SimplePacketBuilder(PacketType.RackMountableData)

    pb.writeTileEntity(t)
    pb.writeInt(mountable)
    pb.writeNBT(t.lastData[mountable])

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendRaidChange(t: TERaid) {
    val pb = SimplePacketBuilder(PacketType.RaidStateChange)

    pb.writeTileEntity(t)
    for (slot in 0 until t.getSizeInventory()) {
      pb.writeBoolean(!t.getStackInSlot(slot).isEmpty)
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendRedstoneState(t: RedstoneAware) {
    val pb = SimplePacketBuilder(PacketType.RedstoneState)

    pb.writeTileEntity(t)
    pb.writeBoolean(t.outputEnabled)
    for (d in EnumFacing.values()) {
      pb.writeByte(t.getOutput(d))
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendRobotAssembling(t: TEAssembler, assembling: Boolean) {
    val pb = SimplePacketBuilder(PacketType.RobotAssemblingState)

    pb.writeTileEntity(t)
    pb.writeBoolean(assembling)

    pb.sendToPlayersNearHost(t)
  }

  fun sendRobotMove(t: TERobot, position: BlockPos, direction: EnumFacing) {
    val pb = SimplePacketBuilder(PacketType.RobotMove)

    // Custom pb.writeTileEntity() with fake coordinates (valid for the client).
    pb.writeInt(t.world!!.provider.dimension)
    pb.writeInt(position.x)
    pb.writeInt(position.y)
    pb.writeInt(position.z)
    pb.writeDirection(direction)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendRobotAnimateSwing(t: TERobot) {
    val pb = SimplePacketBuilder(PacketType.RobotAnimateSwing)

    pb.writeTileEntity(t.proxy!!)
    pb.writeInt(t.animationTicksTotal)

    pb.sendToPlayersNearTileEntity(t, Settings.get.maxNetworkClientEffectPacketDistance)
  }

  fun sendRobotAnimateTurn(t: TERobot) {
    val pb = SimplePacketBuilder(PacketType.RobotAnimateTurn)

    pb.writeTileEntity(t.proxy!!)
    pb.writeByte(t.turnAxis)
    pb.writeInt(t.animationTicksTotal)

    pb.sendToPlayersNearTileEntity(t, Settings.get.maxNetworkClientEffectPacketDistance)
  }

  fun sendRobotInventory(t: TERobot, slot: Int, stack: ItemStack) {
    val pb = SimplePacketBuilder(PacketType.RobotInventoryChange)

    pb.writeTileEntity(t.proxy)
    pb.writeInt(slot)
    pb.writeItemStack(stack)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendRobotLightChange(t: TERobot) {
    val pb = SimplePacketBuilder(PacketType.RobotLightChange)

    pb.writeTileEntity(t.proxy)
    pb.writeInt(t.info.lightColor)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendRobotNameChange(t: TERobot) {
    val pb = SimplePacketBuilder(PacketType.RobotNameChange)

    pb.writeTileEntity(t.proxy)
    val name = t.name()
    val len = name.length
    pb.writeShort(len)
    for (x in 0 until len) {
      pb.writeChar(name[x].code)
    }

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendRobotSelectedSlotChange(t: TERobot) {
    val pb = SimplePacketBuilder(PacketType.RobotSelectedSlotChange)

    pb.writeTileEntity(t.proxy!!)
    pb.writeInt(t.selectedSlot())

    pb.sendToPlayersNearTileEntity(t, Settings.get.maxNetworkClientEffectPacketDistance / 4.0)
  }

  fun sendRotatableState(t: Rotatable) {
    val pb = SimplePacketBuilder(PacketType.RotatableState)

    pb.writeTileEntity(t)
    pb.writeDirection(t.pitch)
    pb.writeDirection(t.yaw)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendSwitchActivity(t: TERelay) {
    val pb = SimplePacketBuilder(PacketType.SwitchActivity)

    pb.writeTileEntity(t)

    pb.sendToPlayersNearTileEntity(t, Settings.get.maxNetworkClientEffectPacketDistance)
  }

  fun appendTextBufferColorChange(pb: PacketBuilder, foreground: PackedColor.Color, background: PackedColor.Color) {
    pb.writePacketType(PacketType.TextBufferMultiColorChange)

    pb.writeInt(foreground.value.toInt())
    pb.writeBoolean(foreground.isPalette)
    pb.writeInt(background.value.toInt())
    pb.writeBoolean(background.isPalette)
  }

  fun appendTextBufferCopy(pb: PacketBuilder, col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int) {
    pb.writePacketType(PacketType.TextBufferMultiCopy)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeInt(w)
    pb.writeInt(h)
    pb.writeInt(tx)
    pb.writeInt(ty)
  }

  fun appendTextBufferDepthChange(pb: PacketBuilder, value: TextBuffer.ColorDepth) {
    pb.writePacketType(PacketType.TextBufferMultiDepthChange)

    pb.writeInt(value.ordinal)
  }

  fun appendTextBufferFill(pb: PacketBuilder, col: Int, row: Int, w: Int, h: Int, c: Int) {
    pb.writePacketType(PacketType.TextBufferMultiFill)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeInt(w)
    pb.writeInt(h)
    pb.writeMedium(c)
  }

  fun appendTextBufferPaletteChange(pb: PacketBuilder, index: Int, color: Int) {
    pb.writePacketType(PacketType.TextBufferMultiPaletteChange)

    pb.writeInt(index)
    pb.writeInt(color)
  }

  fun appendTextBufferResolutionChange(pb: PacketBuilder, w: Int, h: Int) {
    pb.writePacketType(PacketType.TextBufferMultiResolutionChange)

    pb.writeInt(w)
    pb.writeInt(h)
  }

  fun appendTextBufferViewportResolutionChange(pb: PacketBuilder, w: Int, h: Int) {
    pb.writePacketType(PacketType.TextBufferMultiViewportResolutionChange)

    pb.writeInt(w)
    pb.writeInt(h)
  }

  fun appendTextBufferMaxResolutionChange(pb: PacketBuilder, w: Int, h: Int) {
    pb.writePacketType(PacketType.TextBufferMultiMaxResolutionChange)

    pb.writeInt(w)
    pb.writeInt(h)
  }

  fun appendTextBufferSet(pb: PacketBuilder, col: Int, row: Int, s: String, vertical: Boolean) {
    pb.writePacketType(PacketType.TextBufferMultiSet)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeUTF(s)
    pb.writeBoolean(vertical)
  }

  fun appendTextBufferBitBlt(pb: PacketBuilder, col: Int, row: Int, w: Int, h: Int, owner: String, id: Int, fromCol: Int, fromRow: Int) {
    pb.writePacketType(PacketType.TextBufferBitBlt)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeInt(w)
    pb.writeInt(h)
    pb.writeUTF(owner)
    pb.writeInt(id)
    pb.writeInt(fromCol)
    pb.writeInt(fromRow)
  }

  fun appendTextBufferRamInit(pb: PacketBuilder, address: String, id: Int, nbt: NBTTagCompound) {
    pb.writePacketType(PacketType.TextBufferRamInit)

    pb.writeUTF(address)
    pb.writeInt(id)
    pb.writeNBT(nbt)
  }

  fun appendTextBufferRamDestroy(pb: PacketBuilder, owner: String, id: Int) {
    pb.writePacketType(PacketType.TextBufferRamDestroy)
    pb.writeUTF(owner)
    pb.writeInt(id)
  }

  fun appendTextBufferRawSetText(pb: PacketBuilder, col: Int, row: Int, text: Array<IntArray>) {
    pb.writePacketType(PacketType.TextBufferMultiRawSetText)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeShort(text.size)
    for (line in text) {
      pb.writeShort(line.size)
      for (element in line) {
        pb.writeMedium(element)
      }
    }
  }

  fun appendTextBufferRawSetBackground(pb: PacketBuilder, col: Int, row: Int, color: Array<IntArray>) {
    pb.writePacketType(PacketType.TextBufferMultiRawSetBackground)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeShort(color.size)
    for (line in color) {
      pb.writeShort(line.size)
      line.forEach(pb::writeInt)
    }
  }

  fun appendTextBufferRawSetForeground(pb: PacketBuilder, col: Int, row: Int, color: Array<IntArray>) {
    pb.writePacketType(PacketType.TextBufferMultiRawSetForeground)

    pb.writeInt(col)
    pb.writeInt(row)
    pb.writeShort(color.size)
    for (line in color) {
      pb.writeShort(line.size)
      line.forEach(pb::writeInt)
    }
  }

  fun sendTextBufferInit(address: String, value: NBTTagCompound, player: EntityPlayerMP) {
    val pb = CompressedPacketBuilder(PacketType.TextBufferInit)

    pb.writeUTF(address)
    pb.writeNBT(value)

    pb.sendToPlayer(player)
  }

  fun sendTextBufferPowerChange(address: String, hasPower: Boolean, host: EnvironmentHost) {
    val pb = SimplePacketBuilder(PacketType.TextBufferPowerChange)

    pb.writeUTF(address)
    pb.writeBoolean(hasPower)

    pb.sendToPlayersNearHost(host)
  }

  fun sendScreenTouchMode(t: TEScreen, value: Boolean) {
    val pb = SimplePacketBuilder(PacketType.ScreenTouchMode)

    pb.writeTileEntity(t)
    pb.writeBoolean(value)

    pb.sendToPlayersNearTileEntity(t)
  }

  fun sendSound(world: World, x: Double, y: Double, z: Double, sound: ResourceLocation, category: SoundCategory, range: Double) {
    val pb = SimplePacketBuilder(PacketType.SoundEffect)

    pb.writeInt(world.provider.dimension)
    pb.writeDouble(x)
    pb.writeDouble(y)
    pb.writeDouble(z)
    pb.writeUTF(sound.toString())
    pb.writeByte(category.ordinal)
    pb.writeFloat(range.toFloat())

    pb.sendToNearbyPlayers(world, x, y, z, range)
  }

  fun sendSound(world: World, x: Double, y: Double, z: Double, frequency: Int, duration: Int) {
    val pb = SimplePacketBuilder(PacketType.Sound)

    val blockPos = BlockPosition(x, y, z)
    pb.writeInt(world.provider.dimension)
    pb.writeInt(blockPos.x)
    pb.writeInt(blockPos.y)
    pb.writeInt(blockPos.z)
    pb.writeShort(frequency)
    pb.writeShort(duration)

    pb.sendToNearbyPlayers(world, x, y, z, Settings.get.maxNetworkClientSoundPacketDistance)
  }

  fun sendSound(world: World, x: Double, y: Double, z: Double, pattern: String) {
    val pb = SimplePacketBuilder(PacketType.SoundPattern)

    val blockPos = BlockPosition(x, y, z)
    pb.writeInt(world.provider.dimension)
    pb.writeInt(blockPos.x)
    pb.writeInt(blockPos.y)
    pb.writeInt(blockPos.z)
    pb.writeUTF(pattern)

    pb.sendToNearbyPlayers(world, x, y, z, Settings.get.maxNetworkClientSoundPacketDistance)
  }

  fun sendTransposerActivity(t: TETransposer) {
    val pb = SimplePacketBuilder(PacketType.TransposerActivity)

    pb.writeTileEntity(t)

    pb.sendToPlayersNearTileEntity(t, Settings.get.maxNetworkClientEffectPacketDistance / 2.0)
  }

  fun sendWaypointLabel(t: Waypoint) {
    val pb = SimplePacketBuilder(PacketType.WaypointLabel)

    pb.writeTileEntity(t)
    pb.writeUTF(t.label)

    pb.sendToPlayersNearTileEntity(t)
  }
}
