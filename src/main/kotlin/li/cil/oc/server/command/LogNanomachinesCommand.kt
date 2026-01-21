package li.cil.oc.server.command

import li.cil.oc.api.Nanomachines
import li.cil.oc.common.command.SimpleCommand
import li.cil.oc.common.nanomachines.ControllerImpl
import li.cil.oc.server.requireIsPlayer
import net.minecraft.command.ICommandSender
import net.minecraft.command.WrongUsageException
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.server.MinecraftServer
import net.minecraftforge.fml.common.FMLCommonHandler

object LogNanomachinesCommand: SimpleCommand("oc_nanomachines") {
  init {
    aliases += "oc_nm"
  }

  override fun getUsage(source: ICommandSender): String = "$name [player]"

  override fun execute(server: MinecraftServer, source: ICommandSender, command: Array<String>) {
    val source = (if (command.isNotEmpty()) {
      val player = command[0]
      val config = FMLCommonHandler.instance().minecraftServerInstance.playerList
      config.getPlayerByUsername(player)
    } else source);

    val player = source.requireIsPlayer()
    when (val controller = Nanomachines.installController(player)) {
      is ControllerImpl -> controller.print()
      else -> {} // Someone did something.
    }
  }

  // OP levels for reference:
  // 1 - Ops can bypass spawn protection.
  // 2 - Ops can use /clear, /difficulty, /effect, /gamemode, /gamerule, /give, /summon, /setblock and /tp, and can edit command blocks.
  // 3 - Ops can use /ban, /deop, /kick, and /op.
  // 4 - Ops can use /stop.

  override fun getRequiredPermissionLevel(): Int = 2
}
