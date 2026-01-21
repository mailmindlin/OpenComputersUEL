package li.cil.oc.server.command

import li.cil.oc.Settings
import li.cil.oc.Settings.DebugCardAccess
import li.cil.oc.common.command.SimpleCommand
import net.minecraft.command.CommandException
import net.minecraft.command.ICommandSender
import net.minecraft.command.WrongUsageException
import net.minecraft.server.MinecraftServer
import net.minecraft.util.text.TextComponentString

private class NotAllowed: CommandException("")
private class ShowUsage: CommandException("")

object DebugWhitelistCommand: SimpleCommand("oc_debugWhitelist") {
  // Required OP levels:
  //  to revoke your cards - 0
  //  to do other whitelist manipulation - 2

  override fun getRequiredPermissionLevel(): Int = 0
  private fun isOp(sender: ICommandSender): Boolean = sender.canUseCommand(2, this.name)

  override fun getUsage(sender: ICommandSender): String =
    if (isOp(sender)) "$name [revoke|add|remove] <player> OR $name [revoke|list]"
    else "$name revoke"

  override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<String>) {
    val wl = when (val wl = Settings.get.debugCardAccess) {
      is DebugCardAccess.Whitelist -> wl
      else -> throw WrongUsageException("§cDebug card whitelisting is not enabled.")
    }

    fun ICommandSender.requireOp() {
      if (!isOp(this)) {
        throw NotAllowed()
      }
    }

    fun requireOneArg(): String {
      if (args.size != 2) throw ShowUsage()
      return args[1]
    }

    fun revokeUser(player: String) {
      if (wl.isWhitelisted(player)) {
        wl.invalidate(player)
        sender.sendMessage(TextComponentString("§aAll your debug cards were invalidated."))
      } else sender.sendMessage(TextComponentString("§cYou are not whitelisted to use debug card."))
    }

    try {
      when (args.getOrNull(0)) {
        "revoke" -> {
          val target = when (args.size) {
            1 -> sender.name
            2 -> {
              sender.requireOp()
              args[1]
            }
            else -> throw ShowUsage()
          }
          revokeUser(target)
        }
        "list" -> {
          if (args.size != 1) {
            throw ShowUsage()
          }
          sender.requireOp()
          val players = wl.whitelist
          if (players.isNotEmpty())
            sender.sendMessage(TextComponentString("§aCurrently whitelisted players: §e" + players.joinToString(", ")))
          else
            sender.sendMessage(TextComponentString("§cThere is no currently whitelisted players."))
        }
        "add" -> {
          val target = requireOneArg()
          sender.requireOp()
          wl.add(target)
          sender.sendMessage(TextComponentString("§aPlayer was added to whitelist."))
        }
        "remove" -> {
          val target = requireOneArg()
          sender.requireOp()
          wl.remove(target)
          sender.sendMessage(TextComponentString("§aPlayer was removed from whitelist"))
        }
        else -> throw ShowUsage()
      }
    } catch (_: ShowUsage) {
      sender.sendMessage(TextComponentString("§e" + getUsage(sender)))
    } catch (_: NotAllowed) {
      sender.sendMessage(TextComponentString("§eInvalid Permissions"))
    }
  }
}
