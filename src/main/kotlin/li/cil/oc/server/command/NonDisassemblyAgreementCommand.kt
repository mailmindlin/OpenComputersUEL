package li.cil.oc.server.command

import li.cil.oc.Settings
import li.cil.oc.common.command.SimpleCommand
import li.cil.oc.server.requireIsPlayer
import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.server.MinecraftServer

object NonDisassemblyAgreementCommand: SimpleCommand("oc_preventDisassembling", "oc_nodis", "oc_prevdis") {
  override fun getUsage(source: ICommandSender) = "$name <boolean>"

  override fun execute(server: MinecraftServer, source: ICommandSender, command: Array<String>) {
    val player = source.requireIsPlayer()

    val stack = player.heldItemMainhand
    if (!stack.isEmpty) {
      if (!stack.hasTagCompound()) {
        stack.tagCompound = NBTTagCompound()
      }
      val nbt = stack.tagCompound!!
      val preventDisassembly =
        if (command != null && command.isNotEmpty())
          CommandBase.parseBoolean(command[0])
        else
          !nbt.getBoolean(Settings.namespace + "undisassemblable")
      if (preventDisassembly)
        nbt.setBoolean(Settings.namespace + "undisassemblable", true)
      else
        nbt.removeTag(Settings.namespace + "undisassemblable")
      if (nbt.isEmpty) stack.setTagCompound(null)
    }
  }

  // OP levels for reference:
  // 1 - Ops can bypass spawn protection.
  // 2 - Ops can use /clear, /difficulty, /effect, /gamemode, /gamerule, /give, /summon, /setblock and /tp, and can edit command blocks.
  // 3 - Ops can use /ban, /deop, /kick, and /op.
  // 4 - Ops can use /stop.

  override fun getRequiredPermissionLevel(): Int = 2
}
