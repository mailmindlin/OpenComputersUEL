package li.cil.oc.integration.ic2

import ic2.api.reactor.IReactor
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World


class DriverReactor : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = IReactor::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing)
        = (world.getTileEntity(pos) as? IReactor)?.let(::Environment)

    class Environment(tileEntity: IReactor) : ManagedTileEntityEnvironment<IReactor>(tileEntity, "reactor"),
        NamedBlock {
        override fun preferredName(): String = "reactor"

        override fun priority(): Int = 0

        @Callback(doc = "function():number -- Get the reactor's heat.")
        fun getHeat(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.heat)
        }

        @Callback(doc = "function():number -- Get the reactor's maximum heat before exploding.")
        fun getMaxHeat(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.maxHeat)
        }

        @Callback(doc = "function():number -- Get the reactor's energy output. Not multiplied with the base EU/t value.")
        fun getReactorEnergyOutput(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.reactorEnergyOutput)
        }

        @Callback(doc = "function():number -- Get the reactor's base EU/t value.")
        fun getReactorEUOutput(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.reactorEUEnergyOutput)
        }

        @Callback(doc = "function():boolean -- Get whether the reactor is active and supposed to produce energy.")
        fun producesEnergy(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.produceEnergy())
        }
    }
}
