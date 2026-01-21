package li.cil.oc.common.command

import net.minecraft.command.CommandBase
import net.minecraft.command.ICommandSender
import net.minecraft.server.MinecraftServer
import net.minecraftforge.fml.common.FMLCommonHandler

abstract class SimpleCommand(private val commandName: String, vararg aliases: String) : CommandBase() {
    protected val aliases: MutableList<String> = mutableListOf(*aliases)

    override fun getName(): String = commandName

    override fun getAliases(): MutableList<String> = aliases

    override fun checkPermission(server: MinecraftServer, sender: ICommandSender): Boolean {
        return super.checkPermission(server, sender) ||
            (FMLCommonHandler.instance().minecraftServerInstance != null &&
                FMLCommonHandler.instance().minecraftServerInstance.isSinglePlayer)
    }

    override fun isUsernameIndex(command: Array<String>, i: Int): Boolean = false
}