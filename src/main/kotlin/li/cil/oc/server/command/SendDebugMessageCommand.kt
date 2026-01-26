package li.cil.oc.server.command

import li.cil.oc.api.Network
import li.cil.oc.common.command.SimpleCommand
import li.cil.oc.server.network.DebugNetwork
import net.minecraft.command.ICommandSender
import net.minecraft.command.WrongUsageException
import net.minecraft.server.MinecraftServer

object SendDebugMessageCommand: SimpleCommand("oc_sendDebugMessage", "oc_sdbg") {
  override fun getUsage(sender: ICommandSender): String = name + "<destinationAddress> [message...]"

  override fun execute(server: MinecraftServer, sender: ICommandSender, args: Array<out String>?) {
    if (args == null || args.isEmpty()) {
      throw WrongUsageException("no destination address specified.")
    }
    val destination = args[0]
    DebugNetwork.getEndpoint(destination).forEach { endpoint ->
      val packet = Network.newPacket(sender.name, destination, 0, args.sliceArray(1..args.size))!!
      endpoint.receivePacket(packet)
    }
  }

  override fun getRequiredPermissionLevel(): Int = 2
}
