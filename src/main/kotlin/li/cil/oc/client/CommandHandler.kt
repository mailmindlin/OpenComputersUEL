package li.cil.oc.client

import li.cil.oc.common.command.SimpleCommand
import net.minecraft.client.gui.GuiScreen
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraftforge.client.ClientCommandHandler

object CommandHandler {
  fun register() {
    ClientCommandHandler.instance.registerCommand(SetClipboardCommand)
  }

  object SetClipboardCommand: SimpleCommand("oc_setclipboard") {
    override fun getUsage(source: ICommandSender): String = name + " <value>"

    override fun execute(server: MinecraftServer, source: ICommandSender, command: Array<String>?) {
      if (source.getEntityWorld().isRemote && command != null && command.size > 0) {
        GuiScreen.setClipboardString(command[0])
      }
    }

    // OP levels for reference:
    // 1 - Ops can bypass spawn protection.
    // 2 - Ops can use /clear, /difficulty, /effect, /gamemode, /gamerule, /give, /summon, /setblock and /tp, and can edit command blocks.
    // 3 - Ops can use /ban, /deop, /kick, and /op.
    // 4 - Ops can use /stop.

    override fun getRequiredPermissionLevel(): Int = 0
  }

}
