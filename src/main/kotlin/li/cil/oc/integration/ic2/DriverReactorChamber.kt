package li.cil.oc.integration.ic2

import ic2.api.reactor.IReactorChamber
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.prefab.DriverSidedTileEntity
import li.cil.oc.integration.ManagedTileEntityEnvironment
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

class DriverReactorChamber : DriverSidedTileEntity() {
    override fun getTileEntityClass(): Class<*> = IReactorChamber::class.java

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing)
        = (world.getTileEntity(pos) as? IReactorChamber)?.let(::Environment)

    class Environment(tileEntity: IReactorChamber) : ManagedTileEntityEnvironment<IReactorChamber>(tileEntity, "reactor_chamber"), NamedBlock {
        override fun preferredName() = "reactor_chamber"

        override fun priority(): Int = 0

        @Callback(doc = "function():number -- Get the reactor's heat.")
        fun getHeat(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.reactorInstance?.heat ?: 0)
        }

        @Callback(doc = "function():number -- Get the reactor's maximum heat before exploding.")
        fun getMaxHeat(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.reactorInstance?.maxHeat ?: 0)
        }

        @Callback(doc = "function():number -- Get the reactor's energy output. Not multiplied with the base EU/t value.")
        fun getReactorEnergyOutput(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.reactorInstance?.reactorEnergyOutput ?: 0)
        }

        @Callback(doc = "function():number -- Get the reactor's base EU/t value.")
        fun getReactorEUOutput(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.reactorInstance?.reactorEUEnergyOutput ?: 0)
        }

        @Callback(doc = "function():boolean -- Get whether the reactor is active and supposed to produce energy.")
        fun producesEnergy(context: Context?, args: Arguments?): Array<Any> {
            return arrayOf(tileEntity.reactorInstance?.produceEnergy() ?: false)
        }
    }
}
