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
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntityBrewingStand
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverBrewingStand : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = TileEntityBrewingStand::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
        Environment(world.getTileEntity(pos) as TileEntityBrewingStand)

    class Environment(tileEntity: TileEntityBrewingStand) :
        ManagedTileEntityEnvironment<TileEntityBrewingStand>(tileEntity, "brewing_stand"), NamedBlock {

        override fun preferredName(): String = "brewing_stand"

        override fun priority(): Int = 0

        @Callback(doc = "function():number -- Get the number of ticks remaining of the current brewing operation.")
        fun getBrewTime(context: Context, args: Arguments): Result {
            return result(tileEntity.getField(0))
        }
    }

    object Provider : EnvironmentProvider {
        override fun getEnvironment(stack: ItemStack): Class<*>? {
            return if (!stack.isEmpty && stack.item == Items.BREWING_STAND)
                Environment::class.java
            else null
        }
    }
}
