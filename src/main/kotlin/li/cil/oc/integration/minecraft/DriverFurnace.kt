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
import net.minecraft.tileentity.TileEntityFurnace
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverFurnace : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = TileEntityFurnace::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
        Environment(world.getTileEntity(pos) as TileEntityFurnace)

    class Environment(tileEntity: TileEntityFurnace) :
        ManagedTileEntityEnvironment<TileEntityFurnace>(tileEntity, "furnace"), NamedBlock {

        override fun preferredName(): String = "furnace"

        override fun priority(): Int = 0

        @Callback(doc = "function():number -- The number of ticks that the furnace will keep burning from the last consumed fuel.")
        fun getBurnTime(context: Context, args: Arguments): Result {
            return result(tileEntity.getField(0))
        }

        @Callback(doc = "function():number -- The number of ticks that the currently burning fuel lasts in total.")
        fun getCurrentItemBurnTime(context: Context, args: Arguments): Result {
            return result(tileEntity.getField(1))
        }

        @Callback(doc = "function():number -- The number of ticks that the current item has been cooking for.")
        fun getCookTime(context: Context, args: Arguments): Result {
            return result(tileEntity.getField(2))
        }

        @Callback(doc = "function():number -- The number of ticks that the current item needs to cook.")
        fun getTotalCookTime(context: Context, args: Arguments): Result {
            return result(tileEntity.getField(3))
        }

        @Callback(doc = "function():boolean -- Get whether the furnace is currently active.")
        fun isBurning(context: Context, args: Arguments): Result {
            return result(tileEntity.isBurning)
        }
    }

    object Provider : EnvironmentProvider {
        override fun getEnvironment(stack: ItemStack): Class<*>? {
            return if (!stack.isEmpty && Block.getBlockFromItem(stack.item) == Blocks.FURNACE)
                Environment::class.java
            else null
        }
    }
}
