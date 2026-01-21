package li.cil.oc.server.command

import li.cil.oc.common.command.SimpleCommand
import li.cil.oc.server.requireIsPlayer
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.Vec3d

object SpawnComputerCommand: SimpleCommand("oc_spawnComputer", "oc_sc") {
  final val MaxDistance = 16

  override fun getUsage(source: ICommandSender): String = name

  override fun execute(server: MinecraftServer, source: ICommandSender, command: Array<out String>) {
    val player = source.requireIsPlayer()
    val world = player.entityWorld
    val origin = Vec3d(player.posX, player.posY + player.eyeHeight, player.posZ)
    val direction = player.lookVec
    val lookAt = origin.add(direction.x * MaxDistance, direction.y * MaxDistance, direction.z * MaxDistance)
      /*world.rayTraceBlocks(origin, lookAt) match {
        case hit: RayTraceResult if hit.typeOfHit == RayTraceResult.Type.BLOCK =>
          val hitPos = BlockPosition(hit.getBlockPos, world)
          val casePos = hitPos.offset(hit.sideHit)
          val screenPos = casePos.offset(EnumFacing.UP)
          val keyboardPos = screenPos.offset(EnumFacing.UP)

          if (!world.isAirBlock(casePos) || !world.isAirBlock(screenPos) || !world.isAirBlock(keyboardPos)) {
            player.sendMessage(new TextComponentString("Target position obstructed."))
            return
          }

          def rotateProperly(pos: BlockPosition):tileentity.traits.Rotatable = {
            world.getTileEntity(pos) match {
              case rotatable: tileentity.traits.Rotatable =>
                rotatable.setFromEntityPitchAndYaw(player)
                if (!rotatable.validFacings.contains(rotatable.pitch)) {
                  rotatable.pitch = rotatable.validFacings.headOption.getOrElse(EnumFacing.NORTH)
                }
                rotatable.invertRotation()
                rotatable
              case _ => null // not rotatable
            }
          }

          world.setBlock(casePos, api.Items.get(Constants.BlockName.CaseCreative).block())
          rotateProperly(casePos)
          world.setBlock(screenPos, api.Items.get(Constants.BlockName.ScreenTier2).block())
          rotateProperly(screenPos) match {
            case rotatable: tileentity.traits.Rotatable => rotatable.pitch match {
              case EnumFacing.UP | EnumFacing.DOWN =>
                rotatable.pitch = EnumFacing.NORTH
              case _ => // nothing to do here, pitch is fine
            }
            case _ => // ???
          }
          world.setBlock(keyboardPos, api.Items.get(Constants.BlockName.Keyboard).block())
          world.getTileEntity(keyboardPos) match {
            case t: tileentity.traits.Rotatable =>
              t.setFromEntityPitchAndYaw(player)
              t.setFromFacing(EnumFacing.UP)
            case _ => // ???
          }

          api.Network.joinOrCreateNetwork(world.getTileEntity(casePos))

          val apu = api.Items.get(Constants.ItemName.APUCreative).createItemStack(1)
          LuaStateFactory.setDefaultArch(apu)

          InventoryUtils.insertIntoInventoryAt(apu, casePos)
          InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.RAMTier6).createItemStack(2), casePos)
          InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.HDDTier3).createItemStack(1), casePos)
          InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.LuaBios).createItemStack(1), casePos)
          InventoryUtils.insertIntoInventoryAt(api.Items.get(Constants.ItemName.OpenOS).createItemStack(1), casePos)
        case _ => player.sendMessage(new TextComponentString("You need to be looking at a nearby block."))
        }
    }
   */
    TODO()
  }

  // OP levels for reference:
  // 1 - Ops can bypass spawn protection.
  // 2 - Ops can use /clear, /difficulty, /effect, /gamemode, /gamerule, /give, /summon, /setblock and /tp, and can edit command blocks.
  // 3 - Ops can use /ban, /deop, /kick, and /op.
  // 4 - Ops can use /stop.

  override fun getRequiredPermissionLevel(): Int = 2
}
