package li.cil.oc.integration.ic2

import ic2.api.reactor.IReactor
import ic2.core.block.comp.FluidReactorLookup
import ic2.core.block.reactor.tileentity.TileEntityReactorRedstonePort
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

internal class DriverReactorRedstonePort : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = TileEntityReactorRedstonePort::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing)
        = (world.getTileEntity(pos) as? TileEntityReactorRedstonePort)?.let(::EnvironmentReactorRedstonePort)

    class EnvironmentReactorRedstonePort(tileEntity: TileEntityReactorRedstonePort) :
        ManagedTileEntityEnvironment<TileEntityReactorRedstonePort>(tileEntity, "reactor_redstone_port"), NamedBlock {
        override fun preferredName(): String = "reactor_redstone_port"

        override fun priority(): Int = 0

        private val reactor: IReactor?
            get() {
                val lookup = tileEntity.getComponent(FluidReactorLookup::class.java)
                return lookup?.reactor
            }

        @Callback(doc = "function():number -- Get the reactor's heat.")
        fun getHeat(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(reactor?.heat ?: 0)
        }

        @Callback(doc = "function():number -- Get the reactor's maximum heat before exploding.")
        fun getMaxHeat(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(reactor?.maxHeat ?: 0)
        }

        @Callback(doc = "function():number -- Get the reactor's energy output. Not multiplied with the base EU/t value.")
        fun getReactorEnergyOutput(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(reactor?.reactorEnergyOutput ?: 0)
        }

        @Callback(doc = "function():number -- Get the reactor's base EU/t value.")
        fun getReactorEUOutput(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(reactor?.reactorEUEnergyOutput ?: 0)
        }

        @Callback(doc = "function():boolean -- Get whether the reactor is active and supposed to produce energy.")
        fun producesEnergy(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(reactor?.produceEnergy() ?: false)
        }
    }
}
