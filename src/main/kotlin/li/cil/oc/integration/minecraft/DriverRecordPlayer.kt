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
import net.minecraft.block.BlockJukebox
import net.minecraft.init.Blocks
import net.minecraft.item.Item
import net.minecraft.item.ItemRecord
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverRecordPlayer : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = BlockJukebox.TileEntityJukebox::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
        Environment(world.getTileEntity(pos) as BlockJukebox.TileEntityJukebox)

    class Environment(tileEntity: BlockJukebox.TileEntityJukebox) :
        ManagedTileEntityEnvironment<BlockJukebox.TileEntityJukebox>(tileEntity, "jukebox"), NamedBlock {

        override fun preferredName(): String = "jukebox"

        override fun priority(): Int = 0

        @Callback(doc = "function():string -- Get the title of the record currently in the jukebox.")
        fun getRecord(context: Context, args: Arguments): Result? {
            val record = tileEntity.record
            return if (record != null && record.item is ItemRecord) {
                result((record.item as ItemRecord).recordNameLocal)
            } else null
        }

        @Callback(doc = "function() -- Start playing the record currently in the jukebox.")
        fun play(context: Context, args: Arguments): Result? {
            val record = tileEntity.record
            return if (record != null && record.item is ItemRecord) {
                tileEntity.world.playEvent(null, 1010, tileEntity.pos, Item.getIdFromItem(record.item))
                result(true)
            } else null
        }

        @Callback(doc = "function() -- Stop playing the record currently in the jukebox.")
        fun stop(context: Context, args: Arguments): Result? {
            tileEntity.world.playEvent(1010, tileEntity.pos, 0)
            tileEntity.world.playRecord(tileEntity.pos, null)
            return null
        }
    }

    object Provider : EnvironmentProvider {
        override fun getEnvironment(stack: ItemStack): Class<*>? {
            return if (!stack.isEmpty && Block.getBlockFromItem(stack.item) == Blocks.JUKEBOX)
                Environment::class.java
            else null
        }
    }
}
