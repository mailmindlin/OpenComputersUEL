package li.cil.oc.integration.minecraftforge

import li.cil.oc.api.Network
import li.cil.oc.api.driver.DriverBlock
import li.cil.oc.api.driver.NamedBlock
import li.cil.oc.api.machine.Arguments
import li.cil.oc.api.machine.Callback
import li.cil.oc.api.machine.Context
import li.cil.oc.api.network.ManagedEnvironment
import li.cil.oc.api.network.Visibility
import li.cil.oc.api.prefab.AbstractManagedEnvironment
import li.cil.oc.util.ResultWrapper.result
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.energy.CapabilityEnergy
import net.minecraftforge.energy.IEnergyStorage

/**
 * @author Vexatos
 */
object DriverEnergyStorage : DriverBlock {

    override fun worksWith(world: World, pos: BlockPos, side: EnumFacing): Boolean {
        val tile = world.getTileEntity(pos)
        return tile is TileEntity && tile.hasCapability(CapabilityEnergy.ENERGY, side)
    }

    override fun createEnvironment(world: World, pos: BlockPos, side: EnumFacing): ManagedEnvironment? {
        val tile = world.getTileEntity(pos)
        return if (tile is TileEntity && tile.hasCapability(CapabilityEnergy.ENERGY, side)) {
            Environment(tile.getCapability(CapabilityEnergy.ENERGY, side)!!)
        } else {
            null
        }
    }

    class Environment(val storage: IEnergyStorage) : AbstractManagedEnvironment(), NamedBlock {

        init {
            setNode(Network.newNode(this, Visibility.Network).withComponent("energy_device").create())
        }

        @Callback(doc = "function():number -- Returns the amount of stored energy on the connected side.")
        fun getEnergyStored(context: Context, args: Arguments): Array<Any> =
            result(storage.energyStored)

        @Callback(doc = "function():number -- Returns the maximum amount of stored energy on the connected side.")
        fun getMaxEnergyStored(context: Context, args: Arguments): Array<Any> =
            result(storage.maxEnergyStored)

        @Callback(doc = "function():number -- Returns whether this component can have energy extracted from the connected side.")
        fun canExtract(context: Context, args: Arguments): Array<Any> =
            result(storage.canExtract())

        @Callback(doc = "function():number -- Returns whether this component can receive energy on the connected side.")
        fun canReceive(context: Context, args: Arguments): Array<Any> =
            result(storage.canReceive())

        override fun preferredName(): String = "energy_device"

        override fun priority(): Int = 0
    }
}
