package li.cil.oc.integration.minecraft

import li.cil.oc.api.driver.EnvironmentProvider
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntityNote
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverNoteBlock : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = TileEntityNote::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
        Environment(world.getTileEntity(pos) as TileEntityNote)

    class Environment(tileEntity: TileEntityNote) :
        ManagedTileEntityEnvironment<TileEntityNote>(tileEntity, "note_block"), NamedBlock {

        override fun preferredName(): String = "note_block"

        override fun priority(): Int = 0

        @Callback(direct = true, doc = "function():number -- Get the currently set pitch on this note block.")
        fun getPitch(context: Context, args: Arguments): Array<Any?> {
            return result(tileEntity.note + 1)
        }

        @Callback(doc = "function(value:number) -- Set the pitch for this note block. Must be in the interval [1, 25].")
        fun setPitch(context: Context, args: Arguments): Array<Any?> {
            setPitch(args.checkInteger(0))
            return result(true)
        }

        @Callback(doc = "function([pitch:number]):boolean -- Triggers the note block if possible. Allows setting the pitch for to save a tick.")
        fun trigger(context: Context, args: Arguments): Array<Any?> {
            if (args.count() > 0 && args.checkAny(0) != null) {
                setPitch(args.checkInteger(0))
            }
            val world = tileEntity.world
            val pos = tileEntity.pos
            val material = world.getBlockState(pos.add(0, 1, 0)).material
            val canTrigger = material == Material.AIR
            tileEntity.triggerNote(world, pos)
            return result(canTrigger)
        }

        private fun setPitch(value: Int) {
            if (value < 1 || value > 25) {
                throw IllegalArgumentException("invalid pitch")
            }
            tileEntity.note = (value - 1).toByte()
            tileEntity.markDirty()
        }
    }

    object Provider : EnvironmentProvider {
        override fun getEnvironment(stack: ItemStack): Class<*>? {
            return if (!stack.isEmpty && Block.getBlockFromItem(stack.item) == Blocks.NOTEBLOCK)
                Environment::class.java
            else null
        }
    }
}
