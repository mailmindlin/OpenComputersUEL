package li.cil.oc.server.command

import li.cil.oc.api.Nanomachines
import li.cil.oc.common.command.SimpleCommand
import li.cil.oc.common.nanomachines.ControllerImpl
import li.cil.oc.server.requireIsPlayer
import net.minecraft.command.ICommandSender
import net.minecraft.command.WrongUsageException
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString
import scala.util.control.TailCalls.Cont

object DebugNanomachinesCommand: SimpleCommand("oc_debugNanomachines", "oc_dn") {
  override fun getUsage(source: ICommandSender): String = name

  override fun execute(server: MinecraftServer, source: ICommandSender, args: Array<String>) {
    val player = source.requireIsPlayer();

    val controller = Nanomachines.installController(player)
        as? ControllerImpl
        ?: return // Someone did something.
    controller.debug()
    player.sendMessage(TextComponentString("Debug configuration created, see log for mappings."))
  }

  // OP levels for reference:
  // 1 - Ops can bypass spawn protection.
  // 2 - Ops can use /clear, /difficulty, /effect, /gamemode, /gamerule, /give, /summon, /setblock and /tp, and can edit command blocks.
  // 3 - Ops can use /ban, /deop, /kick, and /op.
  // 4 - Ops can use /stop.
  override fun getRequiredPermissionLevel(): Int = 2
}
