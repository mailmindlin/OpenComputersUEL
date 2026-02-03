package li.cil.oc.integration.minecraft

import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.Result
import li.cil.oc.util.result
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntityCommandBlock
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fml.common.FMLCommonHandler

object DriverCommandBlock : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = TileEntityCommandBlock::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
        Environment(world.getTileEntity(pos) as TileEntityCommandBlock)

    class Environment(tileEntity: TileEntityCommandBlock) :
        ManagedTileEntityEnvironment<TileEntityCommandBlock>(tileEntity, "command_block"), NamedBlock {

        override fun preferredName(): String = "command_block"

        override fun priority(): Int = 0

        @Callback(direct = true, doc = "function():string -- Get the command currently set in this command block.")
        fun getCommand(context: Context, args: Arguments): Result {
            return result(tileEntity.commandBlockLogic.command)
        }

        @Callback(doc = "function(value:string) -- Set the specified command for the command block.")
        fun setCommand(context: Context, args: Arguments): Result {
            tileEntity.commandBlockLogic.command = args.checkString(0)
            tileEntity.world.notifyBlockUpdate(
                tileEntity.pos,
                tileEntity.world.getBlockState(tileEntity.pos),
                tileEntity.world.getBlockState(tileEntity.pos),
                3
            )
            return result(true)
        }

        @Callback(doc = "function():number -- Execute the currently set command. This has a slight delay to allow the command block to properly update.")
        fun executeCommand(context: Context, args: Arguments): Result {
            context.pause(0.1)
            return if (!FMLCommonHandler.instance().minecraftServerInstance.isCommandBlockEnabled) {
                result(null, "command blocks are disabled")
            } else {
                val commandSender = tileEntity.commandBlockLogic
                commandSender.trigger(tileEntity.world)
                result(commandSender.successCount, commandSender.lastOutput.unformattedText)
            }
        }
    }

    object Provider : EnvironmentProvider {
        override fun getEnvironment(stack: ItemStack): Class<*>? {
            return if (!stack.isEmpty && Block.getBlockFromItem(stack.item) == Blocks.COMMAND_BLOCK)
                Environment::class.java
            else null
        }
    }
}
