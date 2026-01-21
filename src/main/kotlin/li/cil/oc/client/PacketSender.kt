package li.cil.oc.client

import li.cil.oc.Settings
import li.cil.oc.common.CompressedPacketBuilder
import li.cil.oc.common.PacketType
import li.cil.oc.common.SimplePacketBuilder
import li.cil.oc.common.entity.Drone
import li.cil.oc.common.tileentity.*
import li.cil.oc.common.tileentity.traits.Computer
import net.minecraft.client.Minecraft
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.item.ItemStack
import net.minecraft.init.SoundEvents
import net.minecraft.util.EnumFacing
import net.minecraft.util.SoundCategory

object PacketSender {
  // Timestamp after which the next clipboard message may be sent. Used to
  // avoid spamming large packets on key repeat.
  private var clipboardCooldown = 0L

  fun sendComputerPower(t: Computer, power: Boolean) {
    val pb = SimplePacketBuilder(PacketType.ComputerPower)

    pb.writeTileEntity(t)
    pb.writeBoolean(power)

    pb.sendToServer()
  }

  fun sendDriveMode(unmanaged: Boolean) {
    val pb = SimplePacketBuilder(PacketType.DriveMode)

    pb.writeBoolean(unmanaged)

    pb.sendToServer()
  }

  fun sendDriveLock() {
    val pb = SimplePacketBuilder(PacketType.DriveLock)

    pb.sendToServer()
  }

  fun sendDronePower(e: Drone, power: Boolean) {
    val pb = SimplePacketBuilder(PacketType.DronePower)

    pb.writeEntity(e)
    pb.writeBoolean(power)

    pb.sendToServer()
  }

  fun sendKeyDown(address: String, char: Char, code: Int) {
    val pb = SimplePacketBuilder(PacketType.KeyDown)

    pb.writeUTF(address)
    pb.writeChar(char)
    pb.writeInt(code)

    pb.sendToServer()
  }

  fun sendKeyUp(address: String, char: Char, code: Int) {
    val pb = SimplePacketBuilder(PacketType.KeyUp)

    pb.writeUTF(address)
    pb.writeChar(char)
    pb.writeInt(code)

    pb.sendToServer()
  }

  fun sendClipboard(address: String, value: String?) {
    if (value != null && value.isNotEmpty()) {
      if (value.length > 64 * 1024 || System.currentTimeMillis() < clipboardCooldown) {
        val player = Minecraft.getMinecraft().player
        val handler = Minecraft.getMinecraft().soundHandler
        handler.playSound(PositionedSoundRecord(SoundEvents.BLOCK_NOTE_HARP, SoundCategory.MASTER, 1f, 1f, player.posX.toFloat(), player.posY.toFloat(), player.posZ.toFloat()))
      } else {
        clipboardCooldown = System.currentTimeMillis() + value.length / 10
        value.chunked(16 * 1024).forEach { part ->
          val pb = CompressedPacketBuilder(PacketType.Clipboard)

          pb.writeUTF(address)
          pb.writeUTF(part)

          pb.sendToServer()
        }
      }
    }
  }

  fun sendMachineItemStateRequest(stack: ItemStack) {
    val pb = SimplePacketBuilder(PacketType.MachineItemStateRequest)

    pb.writeItemStack(stack)

    pb.sendToServer()
  }

  fun sendMouseClick(address: String, x: Double, y: Double, drag: Boolean, button: Int) {
    val pb = SimplePacketBuilder(PacketType.MouseClickOrDrag)

    pb.writeUTF(address)
    pb.writeFloat(x.toFloat())
    pb.writeFloat(y.toFloat())
    pb.writeBoolean(drag)
    pb.writeByte(button.toByte())

    pb.sendToServer()
  }

  fun sendMouseScroll(address: String, x: Double, y: Double, scroll: Int) {
    val pb = SimplePacketBuilder(PacketType.MouseScroll)

    pb.writeUTF(address)
    pb.writeFloat(x.toFloat())
    pb.writeFloat(y.toFloat())
    pb.writeByte(scroll.toByte())

    pb.sendToServer()
  }

  fun sendMouseUp(address: String, x: Double, y: Double, button: Int) {
    val pb = SimplePacketBuilder(PacketType.MouseUp)

    pb.writeUTF(address)
    pb.writeFloat(x.toFloat())
    pb.writeFloat(y.toFloat())
    pb.writeByte(button.toByte())

    pb.sendToServer()
  }

  fun sendCopyToAnalyzer(address: String, line: Int) {
    val pb = SimplePacketBuilder(PacketType.CopyToAnalyzer)

    pb.writeUTF(address)
    pb.writeInt(line)

    pb.sendToServer()
  }

  fun sendMultiPlace() {
    val pb = SimplePacketBuilder(PacketType.MultiPartPlace)
    pb.sendToServer()
  }

  fun sendPetVisibility() {
    val pb = SimplePacketBuilder(PacketType.PetVisibility)

    pb.writeBoolean(!Settings.get.hideOwnPet)

    pb.sendToServer()
  }

  fun sendRackMountableMapping(t: Rack, mountableIndex: Int, nodeIndex: Int, side: EnumFacing?) {
    val pb = SimplePacketBuilder(PacketType.RackMountableMapping)

    pb.writeTileEntity(t)
    pb.writeInt(mountableIndex)
    pb.writeInt(nodeIndex)
    pb.writeDirection(side)

    pb.sendToServer()
  }

  fun sendRackRelayState(t: Rack, enabled: Boolean) {
    val pb = SimplePacketBuilder(PacketType.RackRelayState)

    pb.writeTileEntity(t)
    pb.writeBoolean(enabled)

    pb.sendToServer()
  }

  fun sendRobotAssemblerStart(t: Assembler) {
    val pb = SimplePacketBuilder(PacketType.RobotAssemblerStart)

    pb.writeTileEntity(t)

    pb.sendToServer()
  }

  fun sendRobotStateRequest(dimension: Int, x: Int, y: Int, z: Int) {
    val pb = SimplePacketBuilder(PacketType.RobotStateRequest)

    pb.writeInt(dimension)
    pb.writeInt(x)
    pb.writeInt(y)
    pb.writeInt(z)

    pb.sendToServer()
  }

  fun sendServerPower(t: Rack, mountableIndex: Int, power: Boolean) {
    val pb = SimplePacketBuilder(PacketType.ServerPower)

    pb.writeTileEntity(t)
    pb.writeInt(mountableIndex)
    pb.writeBoolean(power)

    pb.sendToServer()
  }

  fun sendTextBufferInit(address: String) {
    val pb = SimplePacketBuilder(PacketType.TextBufferInit)

    pb.writeUTF(address)

    pb.sendToServer()
  }

  fun sendWaypointLabel(t: Waypoint) {
    val pb = SimplePacketBuilder(PacketType.WaypointLabel)

    pb.writeTileEntity(t)
    pb.writeUTF(t.label)

    pb.sendToServer()
  }
}
