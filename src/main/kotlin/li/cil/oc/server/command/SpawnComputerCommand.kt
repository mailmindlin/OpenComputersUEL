package li.cil.oc.server.command

import li.cil.oc.Constants
import li.cil.oc.api.Network
import li.cil.oc.common.command.SimpleCommand
import li.cil.oc.common.tileentity.traits.Rotatable
import li.cil.oc.server.machine.luac.LuaStateFactory
import li.cil.oc.server.requireIsPlayer
import li.cil.oc.util.*
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import net.minecraft.util.text.TextComponentString

object SpawnComputerCommand: SimpleCommand("oc_spawnComputer", "oc_sc") {
  private const val MaxDistance = 16

  override fun getUsage(source: ICommandSender): String = name

  override fun execute(server: MinecraftServer, source: ICommandSender, command: Array<out String>) {
    val player = source.requireIsPlayer()
    val world = player.entityWorld
    val origin = Vec3d(player.posX, player.posY + player.eyeHeight, player.posZ)
    val direction = player.lookVec
    val lookAt = origin.add(direction.x * MaxDistance, direction.y * MaxDistance, direction.z * MaxDistance)
    val hit = world.rayTraceBlocks(origin, lookAt)
      ?.takeIf { it.typeOfHit == RayTraceResult.Type.BLOCK }
      ?: return player.sendMessage(TextComponentString("You need to be looking at a nearby block."))

    val hitPos = BlockPosition(hit.blockPos, world)
    val casePos = hitPos.offset(hit.sideHit)
    val screenPos = casePos.offset(EnumFacing.UP)
    val keyboardPos = screenPos.offset(EnumFacing.UP)

    if (!world.isAirBlock(casePos) || !world.isAirBlock(screenPos) || !world.isAirBlock(keyboardPos)) {
      player.sendMessage(TextComponentString("Target position obstructed."))
      return
    }

    fun rotateProperly(pos: BlockPosition): Rotatable? {
      val rotatable = world.getTileEntity(pos) as? Rotatable
        ?: return null // not rotatable
      rotatable.setFromEntityPitchAndYaw(player)
      if (!rotatable.validFacings.contains(rotatable.pitch)) {
        rotatable.pitch = rotatable.validFacings.firstOrNull() ?: EnumFacing.NORTH
      }
      rotatable.invertRotation()
      return rotatable
    }

    world.setBlock(casePos, Constants.BlockInfo.CaseCreative.block()!!)
    rotateProperly(casePos)
    world.setBlock(screenPos, Constants.BlockInfo.ScreenTier2.block()!!)
    rotateProperly(screenPos)?.let { rotatable ->
      when (rotatable.pitch) {
        EnumFacing.UP, EnumFacing.DOWN -> {
          rotatable.pitch = EnumFacing.NORTH
        }
        else -> {} // nothing to do here, pitch is fine
      }
    } ?: Unit // ???

    world.setBlock(keyboardPos, Constants.BlockInfo.Keyboard.block()!!)
    (world.getTileEntity(keyboardPos) as? Rotatable)?.let { t ->
      t.setFromEntityPitchAndYaw(player)
      t.setFromFacing(EnumFacing.UP)
    } ?: Unit // ???

    Network.joinOrCreateNetwork(world.getTileEntity(casePos))

    val apu = Constants.ItemInfo.APUCreative.createItemStack(1)
    LuaStateFactory.setDefaultArch(apu)

    InventoryUtils.insertIntoInventoryAt(apu, casePos)
    InventoryUtils.insertIntoInventoryAt(Constants.ItemInfo.RAMTier6.createItemStack(2), casePos)
    InventoryUtils.insertIntoInventoryAt(Constants.ItemInfo.HDDTier3.createItemStack(1), casePos)
    InventoryUtils.insertIntoInventoryAt(Constants.ItemInfo.LuaBios.createItemStack(1), casePos)
    InventoryUtils.insertIntoInventoryAt(Constants.ItemInfo.OpenOS.createItemStack(1), casePos)
  }

  // OP levels for reference:
  // 1 - Ops can bypass spawn protection.
  // 2 - Ops can use /clear, /difficulty, /effect, /gamemode, /gamerule, /give, /summon, /setblock and /tp, and can edit command blocks.
  // 3 - Ops can use /ban, /deop, /kick, and /op.
  // 4 - Ops can use /stop.

  override fun getRequiredPermissionLevel(): Int = 2
}
