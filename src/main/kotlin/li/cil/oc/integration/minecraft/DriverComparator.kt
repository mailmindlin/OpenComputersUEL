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
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntityComparator
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

object DriverComparator : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = TileEntityComparator::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment =
        Environment(world.getTileEntity(pos) as TileEntityComparator)

    class Environment(tileEntity: TileEntityComparator) :
        ManagedTileEntityEnvironment<TileEntityComparator>(tileEntity, "comparator"), NamedBlock {

        override fun preferredName(): String = "comparator"

        override fun priority(): Int = 0

        @Callback(doc = "function():number -- Get the strength of the comparators output signal.")
        fun getOutputSignal(context: Context, args: Arguments): Array<Any?> {
            return result(tileEntity.outputSignal)
        }
    }

    object Provider : EnvironmentProvider {
        override fun getEnvironment(stack: ItemStack): Class<*>? {
            return if (!stack.isEmpty && stack.item == Items.COMPARATOR)
                Environment::class.java
            else null
        }
    }
}
